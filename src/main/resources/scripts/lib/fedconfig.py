import vutil, os, sys, re, json
import logging

from archiveutil import DeploymentArchiveAPI
from esapi import EntityStoreAPI

from java.io import File
from java.lang import String
from java.security import KeyFactory, KeyStore

from com.vordel.store.util import ChangeEncryptedFields
from com.vordel.archive.fed import PolicyArchive, EnvironmentArchive, DeploymentArchive
from com.vordel.common.base64 import Encoder, Decoder
from com.vordel.security.openssl import PKCS12

from envconfig import EnvConfig
from envconfig import CertConfig
from envconfig import CertInfo
 
from java.io import File, FileInputStream, FileReader, ByteArrayInputStream
from java.security.cert import CertificateFactory

class FedConfigurator:
    __cert_config = None
    __simulation_mode = False

    __update_cert_config = False
    __expiration_days = -1
    __base_dir = None

    def __init__(self, pol_archive_path, env_archive_path, config_path, cert_config_path = None, properties = None, passphrase = "", confidentials=None):
        self.__passphrase_in = passphrase
        self.__pol_archive = PolicyArchive(pol_archive_path)
        self.__env_archive = EnvironmentArchive(env_archive_path)
        self.__fed_archive = None
        try:
            self.__fed_archive = DeploymentArchive(self.__pol_archive, self.__env_archive, self.__passphrase_in)
        except TypeError:
            # backward compatibility for 7.5.3
            self.__fed_archive = DeploymentArchive(self.__pol_archive, self.__env_archive)
        self.__config = EnvConfig(config_path, properties, confidentials)

        if cert_config_path is not None:
            self.__cert_config = CertConfig(cert_config_path, properties, confidentials)

        logging.info("Deployment archive configuration initialized")
        return

    def enable_cert_config_update(self):
        self.__update_cert_config = True

    def enable_simulation_mode(self):
        self.__simulation_mode = True

    def set_cert_expiration_days(self, days):
        self.__expiration_days = days

    def set_system_properties(self, sys_properties):
        self.__config.set_system_properties(sys_properties)

    def set_base_dir(self, base_dir):
        self.__base_dir = base_dir

    def configure(self, passphrase = ""):
        succeeded = self.__configure_entities()
        if succeeded:
            succeeded = self.__configure_certificates()
            if not succeeded:
                logging.error("Configuration of certificates failed!")
        else:
            logging.error("Configuration of entities failed; check JSON configuration for unconfigured entity fields!")

        if succeeded and self.__passphrase_in != passphrase:
            fed_api = DeploymentArchiveAPI(self.__fed_archive, self.__passphrase_in)
            changer = ChangeEncryptedFields(fed_api.entityStore)
            changer.execute(passphrase, self.__passphrase_in)
            fed_api.deploymentArchive.updateConfiguration(fed_api.entityStore)
            logging.info("Passphrase for output archives changed")

        return succeeded

    def __configure_entities(self):
        logging.info("Configure environmentalized entities")
        fed_api = DeploymentArchiveAPI(self.__fed_archive, self.__passphrase_in)
        env_settings = fed_api.envSettings.getEnvSettings()
        succeeded = True

        config = {}

        for env_entity in env_settings.getEnvironmentalizedEntities():
            env_fields = env_entity.getEnvironmentalizedFields()
            for env_field in env_fields:
                field_value = self.__config.get_value(env_entity, env_field)
                if (field_value.key.type == "reference"):
                    raise ValueError("Reference types are not supported for environmentalization: name=%s; index=%d; type=%s; entity=%s" \
                        % (field_value.key.name, field_value.key.index, field_value.key.type, field_value.key.short_hand_key))

                if (field_value.value is not None):
                    logging.info("Configure field: name=%s; index=%d; type=%s; entity=%s" % (field_value.key.name, field_value.key.index, field_value.key.type, field_value.key.short_hand_key))

                    if not self.__simulation_mode:
                        if field_value.key.short_hand_key not in config:
                            config[field_value.key.short_hand_key] = []

                        if field_value.key.type == "integer":
                            config[field_value.key.short_hand_key].append([field_value.key.name, field_value.key.index, int(field_value.value)])
                        else:
                            config[field_value.key.short_hand_key].append([field_value.key.name, field_value.key.index, str(field_value.value)])
                else:
                    logging.error("Unconfigured field: name=%s; index=%d; type=%s; entity=%s" % (field_value.key.name, field_value.key.index, field_value.key.type, field_value.key.short_hand_key))
                    succeeded = False
    
        if succeeded:
            if not self.__simulation_mode:
                fed_api.addEnvSettings(config)
                logging.info("Environmentalized fields updated.")
            else:
                logging.info("[SIMULATION_MODE] Environmentalized fields simulation succeeded.")
        
        self.__config.update_config_file()

        return succeeded

    def __resolve_file_path(self, file):
        if file and self.__base_dir:
            file = os.path.join(self.__base_dir, file)
        return file

    def __get_certificate_infos(self):
        infos = []
        es = EntityStoreAPI.wrap(self.__fed_archive.getEntityStore(), self.__passphrase_in)
        cert_entities = es.getAll("/[Certificates]name=Certificate Store/[Certificate]**")
        cf = CertificateFactory.getInstance("X.509")
        for cert_entity in cert_entities:
            alias = cert_entity.getStringValue("dname")
            subject = None
            not_after = None

            content = cert_entity.getBinaryValue("content")
            if content:
                cert = cf.generateCertificate(ByteArrayInputStream(content))
                subject = cert.getSubjectDN().getName()
                not_after = cert.getNotAfter()

            infos.append(CertInfo(alias, subject, not_after))
        return infos

    def __get_key_from_p12(self, file, password=None):
        io = FileInputStream(file)
        pkcs12 = PKCS12(io)
        if password is not None:
            try:
                pkcs12.decrypt(String(password).toCharArray())
            except:
                raise ValueError("Invalid passphrase for .p12 certificate!")
        return pkcs12.getKey()

    def __get_cert_from_p12(self, file, password=None):
        ks = KeyStore.getInstance("PKCS12")
        io = FileInputStream(file)
        if password is None:
            ks.load(io, None)
        else:
            ks.load(io, String(password).toCharArray())
        io.close()

        for alias in ks.aliases():
            if ks.isKeyEntry(alias):
                return ks.getCertificate(alias)
        return None

    def __add_or_replace_certificate(self, es, alias, cert, private_key=None):
        cert_store = es.get('/[Certificates]name=Certificate Store')

        # Get or create certificate entity
        cert_entity = es.getChild(cert_store, '[Certificate]dname=%s' % (es.escapeField(alias)))
        if cert_entity is None:
            cert_entity = es.createEntity("Certificate")
            cert_entity.setStringField("dname", alias)
            es.addEntity(cert_store, cert_entity)
            cert_entity = es.getChild(cert_store, '[Certificate]dname=%s' % (es.escapeField(alias)))

        # Set certificate content
        cert_entity.setBinaryValue("content", cert.getEncoded())

        # Set or remove private key
        if private_key is not None:
            cert_entity.setStringField("key", es.encryptBytes(private_key.getEncoded()))
        else:
            entity_private_key = cert_entity.getStringValue("key")
            if not entity_private_key:
                cert_entity.removeField("key")
        
        es.updateEntity(cert_entity)
        return

    def __remove_certificate(self, es, alias):
        # Get certificate entity
        cert_store = es.get('/[Certificates]name=Certificate Store')
        cert_entity = es.getChild(cert_store, '[Certificate]dname=%s' % (es.escapeField(alias)))
        if cert_entity:
            es.cutEntity(cert_entity)
        return
    
    def __configure_certificates(self):
        if self.__cert_config is not None:
            # determine existing certificates
            logging.info("Determine existing certificates")
            cert_infos = self.__get_certificate_infos()
            self.__cert_config.set_cert_infos(cert_infos)
            self.__cert_config.update_config_file()

            # apply configured certificates
            logging.info("Configure certificates")
            certs = self.__cert_config.get_certificates()
            es = EntityStoreAPI.wrap(self.__fed_archive.getEntityStore(), self.__passphrase_in)

            cert_infos = []
            cert = None

            for cert_ref in certs:
                file = self.__resolve_file_path(cert_ref.get_file())
                logging.info("Process alias '%s' (%s): %s" % (cert_ref.get_alias(), cert_ref.get_type(), file))
                if cert_ref.get_type() == "crt":
                    cf = CertificateFactory.getInstance("X.509")
                    if os.path.isfile(file):
                        fis = FileInputStream (file)
                        cert = cf.generateCertificate(fis)
                        self.__add_or_replace_certificate(es, cert_ref.get_alias(), cert)
                    else:
                        if self.__simulation_mode:
                            logging.warning("[SIMULATION_MODE] Certificate file not found, certificate (CRT) ignored: alias=%s" % (cert_ref.get_alias()))
                            continue
                        else:
                            raise ValueError("Certificate file not found for alias '%s': %s" % (cert_ref.get_alias(), file))
                elif cert_ref.get_type() == "p12":
                    if os.path.isfile(file):
                        key = self.__get_key_from_p12(file, cert_ref.get_password())
                        cert = self.__get_cert_from_p12(file, cert_ref.get_password())
                        self.__add_or_replace_certificate(es, cert_ref.get_alias(), cert, key)
                    else:
                        if self.__simulation_mode:
                            logging.warning("[SIMULATION_MODE] Certificate file not found, certificate (P12) ignored: alias=%s" % (cert_ref.get_alias()))
                            continue
                        else:
                            raise ValueError("Certificate file not found for alias '%s': %s" % (cert_ref.get_alias(), file))
                elif cert_ref.get_type() == "empty":
                    self.__remove_certificate(es, cert_ref.get_alias())
                    logging.info("Certificate removed: %s" % (cert_ref.get_alias()))
                    continue
                else:
                    raise ValueError("Unsupported certificate type: %s" % (cert_ref.get_type()))

                subject = cert.getSubjectDN().getName()
                not_after = cert.getNotAfter()

                cert_info = CertInfo(cert_ref.get_alias(), subject, not_after)
                logging.info("Certificate (%s) added/replaced: %s [%s] - %s" % (cert_ref.get_type(), cert_info.get_alias(), cert_info.format_not_after(), cert_info.get_subject()))

                cert_infos.append(cert_info)

            if self.__update_cert_config:
                self.__cert_config.set_update_cert_infos(cert_infos)
                self.__cert_config.update_config_file()

            if self.__expiration_days >= 0:
                logging.info("Checking for certificate expiration within %i days." % (self.__expiration_days))
                has_expired = False
                for cert_info in cert_infos:
                    expiration_days = cert_info.expiration_in_days()
                    if self.__expiration_days > expiration_days:
                        logging.error("Certificate '%s' expires in %i days!" % (cert_info.get_alias(), expiration_days))
                        has_expired = True
                
                if has_expired:
                    raise ValueError("At least one certificate expires in less than %i days; check log file!" % (self.__expiration_days))

            if not self.__simulation_mode:
                DeploymentArchive.updateConfiguration(self.__fed_archive, es.es)
                logging.info("Certificates updated.")
            else:
                logging.info("[SIMULATION_MODE] Certificates simulation succeeded.")
        return True

    def get_unconfigured_fields(self):
        return self.__config.get_unconfigured_fields()

    def write_fed(self, fed_path):
        if "/" not in fed_path and "\\" not in fed_path:
            fed_path = "./" + fed_path
    
        self.__fed_archive.writeToArchiveFile(fed_path)
        logging.info("Deployment archive written to '%s'" % (fed_path))
        return

    def write_env(self, env_path):
        if "/" not in env_path and "\\" not in env_path:
            env_path = "./" + env_path
        env_archive = EnvironmentArchive(self.__fed_archive)
        env_archive.writeToArchiveFile(env_path)
        logging.info("Environment archive written to '%s'" % (env_path))
        return
