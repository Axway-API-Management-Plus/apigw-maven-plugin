import sys
import os
import traceback
import logging
import json
import random
import base64
import re

from optparse import OptionParser
from com.vordel.common.crypto import PasswordCipher
from java.lang import String

class Secrets:
    """Configuration store for secrets.
    The values of the properties are encrypted with a given key.
    """
    __key_check = "axway-maven-plugin"
    __prefix_encrypt = "encrypt:"
    __file_path = None
    __secrets_json = {}
    __cipher = None

    def __init__(self, secrets_key_file, secrets_file_path, create_if_not_exists=False):
        if not secrets_key_file:
            raise ValueError("No key file specified to encrypt/decrypt secrets!")
        if not secrets_file_path:
            raise ValueError("Path to secrets file not specified!")

        # read key
        if not os.path.isfile(secrets_key_file):
            raise ValueError("Key file not found: %s" % (secrets_key_file))
        
        key = None
        with open(secrets_key_file, mode='rb') as pf:
            key = pf.read()

        self.__file_path = secrets_file_path

        self.__cipher = PasswordCipher(key)

        if os.path.isfile(self.__file_path):
            logging.info("Reading secrets file '%s'" % (self.__file_path))
            with open(self.__file_path) as secrets_file:
                self.__secrets_json = json.load(secrets_file)

            if "secrets" not in self.__secrets_json:
                raise ValueError("File '%s' is not a valid secrets file; missing 'secrets' attribute!" % (self.__file_path))

            key_check = self.get_secret("__")
            if not key_check:
                raise ValueError("Invalid secrets file; key check is missing!")

            if key_check != self.__key_check:
                raise ValueError("Invalid key!")

        elif create_if_not_exists:
            self.__secrets_json["secrets"] = {"__" : self.__encrypt_value(self.__key_check)}
            logging.info("Generating empty secrets.")
        else:
            raise ValueError("Secrets file '%s' doesn't exist!" % (self.__file_path))
        return

    def __encrypt_value(self, value, cipher=None):
        if not cipher:
            cipher = self.__cipher
    
        # add salt
        salt = "#{:06d}:".format(random.randint(0,999999))
        value = salt + value

        # encrypt salted value
        value_bytes = cipher.encrypt(value.encode('utf-8'))
        value_b64 = base64.b64encode(value_bytes)

        return value_b64

    def encrypt(self):
        """Encrypt tagged values.
        Encrypt the values of all properties where the value starts with 'encrypt:'.
        """
        for c,v in self.__secrets_json["secrets"].items():
            if v.startswith(self.__prefix_encrypt):
                v = self.__encrypt_value(v[len(self.__prefix_encrypt):])
                self.__secrets_json["secrets"][c] = v
                logging.info("Property '%s' encrypted." % (c))
        return

    def get_secret(self, key):
        if key not in self.__secrets_json["secrets"]:
            return None

        value = self.__secrets_json["secrets"][key]
        if value:
            if value.startswith(self.__prefix_encrypt):
                raise ValueError("Key '%s' is not encrypted, please apply 'encrypt' before using!" % (key))

            # decrypt salted value
            value_bytes = None
            try:
                value_bytes = base64.b64decode(value)
            except Exception as e:
                raise ValueError("Key '%s' has plain text or is invalid!" % (key))

            decoded_bytes = self.__cipher.decrypt(value_bytes)

            try:
                salted = bytearray(decoded_bytes).decode('utf-8')
            except:
                raise ValueError("Invalid key!")

            # remove salt
            if not re.match(r"#\d{6}:", salted):
                raise ValueError("Key '%s' is invalid1" % (key))

            value = salted[8:]

        return value

    def change_key(self, secrets_key_file_new):
        # create new cipher using new key file
        if not os.path.isfile(secrets_key_file_new):
            raise ValueError("Key file not found: %s" % (secrets_key_file_new))
        
        key = None
        with open(secrets_key_file_new, mode='rb') as skf:
            key = skf.read()

        new_cipher = PasswordCipher(key)

        # re-encrypt values
        for s in self.__secrets_json["secrets"]:
            v = self.get_secret(s)
            v = self.__encrypt_value(v, new_cipher)

            self.__secrets_json["secrets"][s] = v

            logging.info("Key changed for property '%s'." % (s))

        self.__cipher = new_cipher
        return

    def write(self):
        json_str = json.dumps(self.__secrets_json, sort_keys=True, indent=2)
        with open(self.__file_path, "w") as secrets_file:
            secrets_file.write(json_str)
        logging.info("Secrets file '%s' updated." % (self.__file_path))
        return

def main_encrypt():
    prog = "encrypt"
    description="Encrypt secrets."
    version = "%prog 1.0.0"
    usage = "%prog OPTIONS"
    epilog = ""
 
    parser = OptionParser(usage=usage, version=version, epilog=epilog, prog=prog, description=description)
    parser.add_option("-v", "--verbose", dest="verbose", help="Enable verbose messages [optional]", action="store_true")
    parser.add_option("--secrets-file", dest="secrets_file", help="Path of JSON file containing confidential properties", metavar="FILEPATH")
    parser.add_option("--secrets-key", dest="secrets_key_file", help="Path to key file to encrypt confidential properties", metavar="FILEPATH")
    parser.add_option("--secrets-key-new", dest="secrets_key_file_new", help="Path to new key file to change key [optional]", metavar="FILEPATH")

    (options, args) = parser.parse_args()

    if not options.secrets_file:
        parser.error("Secrets file is missing!")

    if not options.secrets_key_file:
        parser.error("Key file is missing!")

    logging.basicConfig(format='%(levelname)s: %(message)s')
    if options.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    else:
        logging.getLogger().setLevel(logging.INFO)

    try:
        secrets = Secrets(options.secrets_key_file, options.secrets_file, create_if_not_exists=True)
        secrets.encrypt()
        if options.secrets_key_file_new:
            logging.info("Re-encrypt values to change key.")
            secrets.change_key(options.secrets_key_file_new)
        secrets.write()

    except Exception as e:
        if options.verbose:
            logging.error("Error occurred, check details:", exc_info=True)
        else:
            logging.error("%s" % (e))
        sys.exit(1) 

    sys.exit(0)


if __name__ == "__main__":
    main_encrypt()
