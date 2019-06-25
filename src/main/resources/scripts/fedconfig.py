import vutil, os, sys, re, json
 
from archiveutil import DeploymentArchiveAPI
from esapi import EntityStoreAPI

from java.io import File
from java.lang import String
from java.security import KeyFactory, KeyStore

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

    def __init__(self, pol_archive_path, env_archive_path, config_path, cert_config_path = None, property_path = None, passphase = ""):
        self.__pol_archive = PolicyArchive(pol_archive_path)
        self.__env_archive = EnvironmentArchive(env_archive_path)
        self.__fed_archive = DeploymentArchive(self.__pol_archive, self.__env_archive)
        self.__config = EnvConfig(config_path, property_path)

        if cert_config_path is not None:
            self.__cert_config = CertConfig(cert_config_path, property_path)
        
        self.__passphrase = passphase
        print ""
        print "INFO : Deployment archive configuration initialized"
        return

    def update_templates(self):
        fed_api = DeploymentArchiveAPI(self.__fed_archive, self.__passphrase)
        env_settings = fed_api.envSettings.getEnvSettings()

        for env_entity in env_settings.getEnvironmentalizedEntities():
            env_fields = env_entity.getEnvironmentalizedFields()
            for env_field in env_fields:
                field_value = self.__config.check_field(env_entity, env_field)
                if (field_value.key.type == "reference"):
                    raise ValueError("Reference types are not supported for environmentalization: name=%s; index=%d; type=%s; entity=%s" \
                        % (field_value.key.name, field_value.key.index, field_value.key.type, field_value.key.short_hand_key))
            
        self.__config.update_config_file(force=True)

        if self.__cert_config is not None:
            cert_infos = self.__get_certificate_infos()
            self.__cert_config.set_cert_infos(cert_infos)
            self.__cert_config.update_config_file()
        return

    def configure_entities(self):
        print "INFO : Configure environmentalized entities"
        fed_api = DeploymentArchiveAPI(self.__fed_archive, self.__passphrase)
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
                    print "INFO : Configure field: name=%s; index=%d; type=%s; entity=%s" % (field_value.key.name, field_value.key.index, field_value.key.type, field_value.key.short_hand_key)

                    if field_value.key.short_hand_key not in config:
                        config[field_value.key.short_hand_key] = []

                    if field_value.key.type == "integer":
                        config[field_value.key.short_hand_key].append([field_value.key.name, field_value.key.index, int(field_value.value)])
                    else:
                        config[field_value.key.short_hand_key].append([field_value.key.name, field_value.key.index, str(field_value.value)])
                else:
                    print "ERROR: Unconfigured field: name=%s; index=%d; type=%s; entity=%s" % (field_value.key.name, field_value.key.index, field_value.key.type, field_value.key.short_hand_key)
                    succeeded = False
    
        if succeeded:
            fed_api.addEnvSettings(config)
            print "INFO : Environmentalized fields updated."
        
        self.__config.update_config_file()

        return succeeded

    def __get_certificate_infos(self):
        infos = []
        es = EntityStoreAPI.wrap(self.__fed_archive.getEntityStore(), self.__passphrase)
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
            pkcs12.decrypt(String(password).toCharArray())
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

        # Set certificate content
        cert_entity.setBinaryValue("content", cert.getEncoded())

        # Set or remove private key
        if private_key is not None:
            cert_entity.setStringField("key", es.encryptBytes(private_key.getEncoded()))
        else:
            entity_private_key = cert_entity.getStringValue("key")
            if not entity_private_key:
                cert_entity.removeField("key")
        return

    def configure_certificates(self):
        if self.__cert_config is not None:
            # determine existing certificates
            cert_infos = self.__get_certificate_infos()
            self.__cert_config.set_cert_infos(cert_infos)
            self.__cert_config.update_config_file()

            # apply configured certificates
            certs = self.__cert_config.get_certificates()
            es = EntityStoreAPI.wrap(self.__fed_archive.getEntityStore(), self.__passphrase)

            cert_infos = []
            cert = None

            for cert_ref in certs:
                if cert_ref.get_type() == "crt":
                    cf = CertificateFactory.getInstance("X.509")
                    fis = FileInputStream (cert_ref.get_file())
                    cert = cf.generateCertificate(fis)
                    self.__add_or_replace_certificate(es, cert_ref.get_alias(), cert)
                    print "INFO : Certificate (CRT) added: alias=%s" % (cert_ref.get_alias())
                elif cert_ref.get_type() == "p12":
                    key = self.__get_key_from_p12(cert_ref.get_file(), cert_ref.get_password())
                    cert = self.__get_cert_from_p12(cert_ref.get_file(), cert_ref.get_password())
                    self.__add_or_replace_certificate(es, cert_ref.get_alias(), cert, key)
                    print "INFO : Certificate (P12) added: alias=%s" % (cert_ref.get_alias())
                else:
                    raise ValueError("Unsupported certificate type: %s" % (cert_ref.get_type()))

                subject = cert.getSubjectDN().getName()
                not_after = cert.getNotAfter()

                cert_infos.append(CertInfo(cert_ref.get_alias(), subject, not_after))

            self.__cert_config.set_update_cert_infos(cert_infos)
            self.__cert_config.update_config_file()

            DeploymentArchive.updateConfiguration(self.__fed_archive, es.es)
            print "INFO : Certificates updated."
        return True

    def get_unconfigured_fields(self):
        return self.__config.get_unconfigured_fields()

    def write_fed(self, fed_path):
        if "/" not in fed_path and "\\" not in fed_path:
            fed_path = "./" + fed_path
        self.__fed_archive.writeToArchiveFile(fed_path)
        print "INFO : Deployment archive written to '%s'" % (fed_path)
        return

    def write_env(self, env_path):
        if "/" not in env_path and "\\" not in env_path:
            env_path = "./" + env_path
        env_archive = EnvironmentArchive(self.__fed_archive)
        env_archive.writeToArchiveFile(env_path)
        print "INFO : Environment archive written to '%s'" % (env_path)
        return
