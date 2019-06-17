import vutil, os, sys, re, json
 
from archiveutil import DeploymentArchiveAPI
from esapi import EntityStoreAPI

from java.io import File
from com.vordel.archive.fed import PolicyArchive, EnvironmentArchive, DeploymentArchive
from com.vordel.common.base64 import Encoder, Decoder

from envconfig import EnvConfig
from envconfig import CertConfig
from envconfig import CertInfo
from addCert import CertStoreUtil
 
from java.io import File, FileInputStream, FileReader, ByteArrayInputStream
from java.security.cert import CertificateFactory

class FedConfigurator:
    __cert_config = None

    def __init__(self, pol_archive_path, env_archive_path, config_path, cert_config_path = None, property_path = None, passphase = ""):
        self.__pol_archive = PolicyArchive(pol_archive_path)
        self.__env_archive = EnvironmentArchive(env_archive_path)
        self.__fed_archive = DeploymentArchive(self.__pol_archive, self.__env_archive)
        self.__config = EnvConfig(config_path, property_path)

        if cert_config_path != None:
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

        if self.__cert_config != None:
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

                if (field_value.value != None):
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
                cert = cf.generateCertificate(ByteArrayInputStream(cert_entity.getBinaryValue("content")))
                subject = cert.getSubjectDN().getName()
                not_after = cert.getNotAfter()

            infos.append(CertInfo(alias, subject, not_after))
        return infos

    def configure_certificates(self):
        if self.__cert_config != None:
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
                    CertStoreUtil.addCertToStore(es, cert_ref.get_alias(), cert)
                    print "INFO : Certificate (CRT) added: alias=%s" % (cert_ref.get_alias())
                elif cert_ref.get_type() == "p12":
                    key = CertStoreUtil.getKeyFromP12(cert_ref.get_file(), cert_ref.get_password())
                    cert = CertStoreUtil.getCertFromP12(cert_ref.get_file(), cert_ref.get_password())
                    CertStoreUtil.addCertToStore(es, cert_ref.get_alias(), cert, key)
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
