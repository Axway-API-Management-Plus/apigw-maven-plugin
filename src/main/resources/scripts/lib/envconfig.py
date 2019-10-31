import json
import os
import logging

from com.vordel.es.xes import PortableESPKFactory;
from java.text import SimpleDateFormat
from java.util import Date
from java.util.concurrent import TimeUnit

class FieldKey:
    def __init__(self, entity_short_hand_key, field_name, field_index, field_type):
        self.short_hand_key = entity_short_hand_key
        self.name = field_name
        self.index = field_index
        self.type = field_type

class FieldValue:
    def __init__(self, field_key, value):
        self.key = field_key
        self.value = value


class PropConfig:
    __properties = {}

    def __init__(self, property_file_path_list):
        if not property_file_path_list:
            return

        for property_file_path in property_file_path_list:
            if not os.path.exists(property_file_path):
                raise ValueError("Property file '%s' doesn't exist!" % (property_file_path))

            logging.info("Reading property file '%s'" % (property_file_path))
            with open(property_file_path) as property_file:
                properties_json = json.load(property_file)

                if "properties" not in properties_json:
                    raise ValueError("File '%s' is not a valid property file; missing 'properties' attribute!" % (property_file_path))
                
                for p in properties_json["properties"]:
                    self.__properties[p] = properties_json["properties"][p]
        return

    def get_property(self, key):
        if key not in self.__properties:
            return None
        return self.__properties[key]

    def set_property(self, key, value):
        self.__properties[key] = value

class EnvConfig:
    __config_file_path = None
    __config_json = None
    __properties = None

    __missing_vars = False
    __file_updated = False

    __unconfigured_fields = []
        
    def __init__(self, config_file_path, properties):
        self.__properties = properties
        self.__pespk_factory = PortableESPKFactory.newInstance()

        self.__config_file_path = config_file_path
        if os.path.isfile(self.__config_file_path):
            logging.info("Reading configuration file '%s'" % (self.__config_file_path))
            with open(self.__config_file_path) as config_file:
                self.__config_json = json.load(config_file)
            self.reset_usage()
        else:
            logging.info("Configuration file '%s' doesn't exist; empty file will be created." % (self.__config_file_path))
            self.__config_json = {}
        return

    def __get_entities(self):
        json_entities = None
        if "entities" in self.__config_json:
            json_entities = self.__config_json["entities"]
        else:
            self.__config_json["entities"] = {}
            self.__missing_vars = True
            json_entities = self.__config_json["entities"]
        return json_entities

    def __get_entity(self, field_key):
        json_entities = self.__get_entities()
        json_entity = None
        if field_key.short_hand_key in json_entities:
            json_entity = json_entities[field_key.short_hand_key]
        else:
            self.__missing_vars = True
            json_entity = {"description": "", "fields": {}}
            json_entities[field_key.short_hand_key] = json_entity
        return json_entity

    def __get_field(self, field_key):
        json_entity = self.__get_entity(field_key)
        fpk = field_key.name + "#" + str(field_key.index)

        json_field = None
        if fpk in json_entity["fields"]:
            json_field = json_entity["fields"][fpk]
            json_field["used"] = True
        else:
            json_field = {"type": field_key.type, "value": None, "property": None, "used": True}
            json_entity["fields"][fpk] = json_field
            self.__missing_vars = True
        return json_field

    def __get_property(self, p):
        v = None

        if self.__properties:
            v = self.__properties.get_property(p)

        if not v:
            if "properties" in self.__config_json and p in self.__config_json["properties"]:
                v = self.__config_json["properties"][p]
        return v

    def reset_usage(self):
        json_entities = self.__get_entities()
        for entity in json_entities.values():
                if "fields" in entity:
                    for field in entity["fields"].values():
                        if "used" in field:
                            field["used"] = False
        return

    def has_unused_vars(self):
        json_entities = self.__get_entities()
        for entity in json_entities.values():
                if "fields" in entity:
                    for field in entity["fields"].values():
                        if "used" in field:
                            if field["used"] == False:
                                return True
        return False

    def to_shorthandkey(self, entity):
        pespk = self.__pespk_factory.createPortableESPK(entity.getEntityPk())
        return pespk.toShorthandString()

    def check_field(self, entity, entity_field):
        fk = FieldKey(self.to_shorthandkey(entity), entity_field.getEntityFieldName(), entity_field.getIndex(), entity_field.getType())
        f = self.__get_field(fk)
        return FieldValue(fk, None)

    def get_value(self, entity, entity_field):
        fk = FieldKey(self.to_shorthandkey(entity), entity_field.getEntityFieldName(), entity_field.getIndex(), entity_field.getType())
        f = self.__get_field(fk)

        value = None
        if "property" in f and f["property"] is not None:
            p = f["property"]
            value = self.__get_property(p)
            if not value:
                raise ValueError("Missing configured property '%s'" % (p))
        else:
            value = f["value"]

        if value is None:
            self.__unconfigured_fields.append(fk)

        return FieldValue(fk, value)

    def get_unconfigured_fields(self):
        return self.__unconfigured_fields

    def update_config_file(self, force=False):
        if self.__missing_vars or self.has_unused_vars() or force:
            with open(self.__config_file_path, "w") as config_file:
                json.dump(self.__config_json, config_file, sort_keys=True, indent=2)
            self.__file_updated = True
            logging.info("Configuration file updated: %s" % (self.__config_file_path))
        return self.__file_updated

    def is_config_file_updated(self):
        return self.__file_updated

class CertRef:
    def __init__(self, alias, cert_type, cert_file_path, password = ""):
        self.__alias = alias
        self.__type = cert_type
        self.__file_path = cert_file_path
        self.__password = password

    def get_alias(self):
        return self.__alias

    def get_type(self):
        return self.__type

    def get_file(self):
        return self.__file_path

    def get_password(self):
        return self.__password

class CertInfo:
    __alias = None
    __subject = None
    __not_after = None
    def __init__(self, alias, subject, not_after):
        self.__alias = alias
        self.__subject = subject
        self.__not_after = not_after

    def get_alias(self):
        return self.__alias

    def get_subject(self):
        return self.__subject

    def get_info(self):
        return {"info": {"subject": self.__subject, "not_after": self.format_not_after() }}
    
    def format_not_after(self):
        """
        Formats the expiration date/time of the certificate.
        """
        df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        return df.format(self.__not_after)

    def expiration_in_days(self):
        """
        Return the number of days until the certificate expires.
        If the certificate is already expired a negative number is returned.
        """
        now = Date()
        diff = self.__not_after.getTime() - now.getTime()
        return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

class CertConfig:
    __config_file_path = None
    __config_json = None
    __properties = None

    def __init__(self, config_file_path, properties):
        self.__properties = properties
        self.__config_file_path = config_file_path
        if os.path.isfile(self.__config_file_path):
            with open(self.__config_file_path) as config_file:
                logging.info("Reading certificate configuration '%s'." % (self.__config_file_path))
                self.__config_json = json.load(config_file)

                if "certificates" not in self.__config_json:
                    raise ValueError("File '%s' is not a valid certification config file; missing 'certificates' attribute!" % (self.__config_file_path))
        else:
            logging.info("Certificate configuration file '%s' doesn't exist; empty file will be created." % (self.__config_file_path))
            self.__config_json = {"certificates": {}}
        return

    def __get_property(self, p):
        v = None
        if self.__properties:
            v = self.__properties.get_property(p)

        if not v:
            if "properties" in self.__config_json and p in self.__config_json["properties"]:
                v = self.__config_json["properties"][p]
        return v

    def set_cert_infos(self, cert_infos):
        certificates = self.__config_json["certificates"]

        # clear existing certificate infos
        for alias, cert_cfg in certificates.items():
            if "origin" in cert_cfg:
                cert_cfg.pop("origin")

        # set certificate infos
        for info in cert_infos:
            if info.get_alias() in certificates:
                certificates[info.get_alias()]["origin"] = info.get_info()
            else:
                certificates[info.get_alias()] = { "origin": info.get_info() }
        return

    def set_update_cert_infos(self, cert_infos):
        certificates = self.__config_json["certificates"]

        for info in cert_infos:
            if info.get_alias() in certificates:
                if "update" in certificates[info.get_alias()]:
                    certificates[info.get_alias()]["update"].update(info.get_info())
        return

    def get_certificates(self):
        certs = []
        if "certificates" in self.__config_json:
            for alias, cert_cfg in self.__config_json["certificates"].items():
                if "update" not in cert_cfg:
                    raise ValueError("Certificate update not configured for alias '%s'!" % (alias))
                
                if cert_cfg["update"] is None:
                    continue

                cert = cert_cfg["update"]

                cert_type = cert["type"]
                if cert_type not in ["crt", "p12"]:
                    raise ValueError("Invalid certificate type '%s' for alias '%s'!" % (cert_type, alias))
                cert_file = cert["file"]

                password = None
                if "password-property" in cert:
                    p = cert["password-property"]
                    password = self.__get_property(p)
                    if not password:
                        raise ValueError("Missing configured property '%s'" % (p))
                elif "password" in cert:
                    password = cert["password"]

                c = CertRef(alias, cert_type, cert_file, password)
                certs.append(c)
        return certs

    def update_config_file(self):
        with open(self.__config_file_path, "w") as cert_file:
            json.dump(self.__config_json, cert_file, sort_keys=True, indent=2)
        logging.info("Certificate configuration file updated: %s" % (self.__config_file_path))
        return
