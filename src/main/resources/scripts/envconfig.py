import json
import os
from com.vordel.es.xes import PortableESPKFactory;
from java.text import SimpleDateFormat

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
    __properties_json = None

    def __init__(self, property_file_path):
        with open(property_file_path) as property_file:
            self.__properties_json = json.load(property_file)

        if "properties" not in self.__properties_json:
            raise ValueError("File '%s' is not a valid property file; missing 'properties' attribute!" % (property_file_path))

        return

    def get_property(self, key):
        if key not in self.__properties_json["properties"]:
            return None
        return self.__properties_json["properties"][key]


class EnvConfig:
    __config_file_path = None
    __config_json = None
    __properties = None

    __missing_vars = False
    __file_updated = False

    __unconfigured_fields = []
        
    def __init__(self, config_file_path, property_file_path = None):
        self.__pespk_factory = PortableESPKFactory.newInstance()

        self.__config_file_path = config_file_path
        if os.path.isfile(self.__config_file_path):
            with open(self.__config_file_path) as config_file:
                self.__config_json = json.load(config_file)
            self.reset_usage()
        else:
            self.__config_json = {}

        if (property_file_path != None):
            self.__properties = PropConfig(property_file_path)

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
        if "properties" not in self.__config_json:
            return None
        if p not in self.__config_json["properties"]:
            return None
        return self.__config_json["properties"][p]

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
        if "property" in f and f["property"] != None:
            p = f["property"]
            value = self.__properties.get_property(p)
            if not value:
                value = self.__get_property(p)
                if not value:
                    raise ValueError("Missing configured property '%s'" % (p))
        else:
            value = f["value"]

        if value == None:
            self.__unconfigured_fields.append(fk)

        return FieldValue(fk, value)

    def get_unconfigured_fields(self):
        return self.__unconfigured_fields

    def update_config_file(self, force=False):
        if self.__missing_vars or self.has_unused_vars() or force:
            with open(self.__config_file_path, "w") as config_file:
                json.dump(self.__config_json, config_file, sort_keys=True, indent=2)
            self.__file_updated = True
            print "INFO : Configuration file updated: %s" % (self.__config_file_path)
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
    alias = None
    subject = None
    not_after = None
    def __init__(self, alias, subject, not_after):
        self.alias = alias
        self.subject = subject
        self.not_after = not_after

    def get_info(self):
        df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        return {"info": {"subject": self.subject, "not_after": df.format(self.not_after) }} 

class CertConfig:
    __config_file_path = None
    __config_json = None
    __properties = None

    def __init__(self, config_file_path, property_file_path = None):
        self.__config_file_path = config_file_path
        if os.path.isfile(self.__config_file_path):
            with open(config_file_path) as config_file:
                self.__config_json = json.load(config_file)

                if "certificates" not in self.__config_json:
                    raise ValueError("File '%s' is not a valid certification config file; missing 'certificates' attribute!" % (config_file_path))
        else:
            self.__config_json = {"certificates": {}}

        if (property_file_path != None):
            self.__properties = PropConfig(property_file_path)

        return

    def __get_property(self, p):
        if "properties" not in self.__config_json:
            return None
        if p not in self.__config_json["properties"]:
            return None
        return self.__config_json["properties"][p]

    def set_cert_infos(self, cert_infos):
        certificates = self.__config_json["certificates"]

        # clear existing certificate infos
        for alias, cert_cfg in certificates.items():
            if "origin" in cert_cfg:
                cert_cfg.pop("origin")

        # set certificate infos
        for info in cert_infos:
            if info.alias in certificates:
                certificates[info.alias]["origin"] = info.get_info()
            else:
                certificates[info.alias] = { "origin": info.get_info() }
        return

    def set_update_cert_infos(self, cert_infos):
        certificates = self.__config_json["certificates"]

        for info in cert_infos:
            if info.alias in certificates:
                if "update" in certificates[info.alias]:
                    certificates[info.alias]["update"].update(info.get_info())
        return

    def get_certificates(self):
        certs = []
        if "certificates" in self.__config_json:
            for alias, cert_cfg in self.__config_json["certificates"].items():
                if "update" not in cert_cfg:
                    raise ValueError("Certificate update not configured for alias '%s'!" % (alias))
                
                if cert_cfg["update"] == None:
                    continue

                cert = cert_cfg["update"]

                cert_type = cert["type"]
                if cert_type not in ["crt", "p12"]:
                    raise ValueError("Invalid certificate type '%s' for alias '%s'!" % (cert_type, alias))
                cert_file = cert["file"]

                password = None
                if "password-property" in cert:
                    p = cert["password-property"]
                    password = self.__properties.get_property(p)
                    if not password:
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
        print "INFO : Certificate configuration file updated: %s" % (self.__config_file_path)
        return
