import sys
import traceback
import logging

from optparse import OptionParser
from fedconfig import FedConfigurator
from envconfig import PropConfig
from datetime import datetime

def parse_system_property(sys_prop):
    if not sys_prop:
        logging.error("Empty system property not allowed!")
        exit(1)

    s = sys_prop.split(":", 1)
    if not s[0]:
        logging.error("Empty system property name!")
        exit(1)

    name = s[0]
    value = s[1] if len(s) > 1 else None

    return (name, value)


def main():
    prog = sys.argv[0]
    version = "%prog 1.1.0"
    usage = "%prog OPTIONS"
    epilog = "Build configured .fed package."
 
    parser = OptionParser(usage=usage, version=version, epilog=epilog)
    parser.add_option("-v", "--verbose", dest="verbose", help="Enable verbose messages [optional]", action="store_true")
    parser.add_option("-e", "--env", dest="env_file_path", help="Path of environment archive (.env)", metavar="FILEPATH")
    parser.add_option("-p", "--pol", dest="pol_file_path", help="Path of policy archive (.pol)", metavar="FILEPATH")
    parser.add_option("-c", "--config", dest="config_file_path", help="Path of JSON configuration file", metavar="FILEPATH")
    parser.add_option("--prop", dest="prop_file_path_list", help="Path of JSON property file [multiple]", metavar="FILEPATH", action="append")
    parser.add_option("--cert", dest="cert_file_path", help="Path of JSON certificate configuration file [optional]", metavar="FILEPATH")
    parser.add_option("--cert-expiration", dest="cert_expiration_days", help="Check if certificates expire within the next days [optional]", metavar="DAYS")
    parser.add_option("--cert-config-update", dest="cert_config_update", help="Enable writing of info section for 'update' certificates within the configuration file [optional]", action="store_true")
    parser.add_option("--output-fed", dest="out_fed_file_path", help="Path of output deployment archive file (.fed) [optional]", metavar="FILEPATH")
    parser.add_option("--output-env", dest="out_env_file_path", help="Path of output environment archive file (.env) [optional]", metavar="FILEPATH")
    parser.add_option("-D", "--define", dest="sys_properties", help="Define a system property [multiple]", metavar="NAME:VALUE", action="append")
    parser.add_option("--passphrase-in", dest="passphrase_in", help="Passphrase of input archive files [optional]", metavar="PASSPHRASE")
    parser.add_option("--passphrase-out", dest="passphrase_out", help="Passphrase for output archive files [optional]", metavar="PASSPHRASE")
    parser.add_option("-s", "--simulate", dest="simulate", help="Enable simulation mode [optional]", action="store_true")
    (options, args) = parser.parse_args()

    if not options.env_file_path:
        parser.error("Environment archive option is missing!")
    if not options.pol_file_path:
        parser.error("Policy archive option is missing!")
    if not options.config_file_path:
        parser.error("Configuration file option is missing!")

    logging.basicConfig(format='%(levelname)s: %(message)s')
    if options.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    else:
        logging.getLogger().setLevel(logging.INFO)

    # Add some standard system properties
    sys_properties = {}
    sys_properties["_system.build.datetime"] = datetime.now().isoformat()

    if options.sys_properties:
        for sp in options.sys_properties:
            (name, value) = parse_system_property(sp)
            sys_properties[name] = value

    try:
        # Set properties from files and system properties
        properties = PropConfig(options.prop_file_path_list)
        for name, value in sys_properties.items():
            logging.info("Provided system property %s" % (name))
            properties.set_property(name, value)

        # Set passphrases
        passphrase_in = ""
        if options.passphrase_in:
            passphrase_in = options.passphrase_in

        passphrase_out = ""
        if options.passphrase_out:
            passphrase_out = options.passphrase_out

        # Execute configuration
        fed_config = FedConfigurator(options.pol_file_path, options.env_file_path, options.config_file_path, options.cert_file_path, properties, passphrase_in)
        if options.simulate:
            fed_config.enable_simulation_mode()

        if options.cert_config_update:
            fed_config.enable_cert_config_update()

        if options.cert_expiration_days:
            fed_config.set_cert_expiration_days(int(options.cert_expiration_days))

        succeeded = fed_config.configure(passphrase_out)
        if succeeded:
            if options.simulate:
                logging.info("[SIMULATION_MODE] No output files written!")
            else:
                if options.out_fed_file_path:
                    fed_config.write_fed(options.out_fed_file_path)
                if options.out_env_file_path:
                    fed_config.write_env(options.out_env_file_path)
        else:
            sys.exit(1)

    except Exception as e:
        if options.verbose:
            logging.error("Error occurred, check details:", exc_info=True)
        else:
            logging.error("%r" % (e))
        sys.exit(1) 

    sys.exit(0)

if __name__ == "__main__":
    main()
