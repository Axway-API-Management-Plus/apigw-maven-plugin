import sys
import json
import traceback
 
from optparse import OptionParser
from fedconfig import FedConfigurator

def main():
    prog = sys.argv[0]
    version = "%prog 1.0.0"
    usage = "%prog OPTIONS"
 
    parser = OptionParser(usage=usage, version=version)
    parser.add_option("-v", "--verbose", dest="verbose", help="Enable verbose messages [optional]", action="store_true")
    parser.add_option("-e", "--env", dest="env_file_path", help="Path of environment archive (.env)", metavar="FILEPATH")
    parser.add_option("-p", "--pol", dest="pol_file_path", help="Path of policy archive (.pol)", metavar="FILEPATH")
    parser.add_option("-c", "--config", dest="config_file_path", help="Path of JSON configuration file", metavar="FILEPATH")
    parser.add_option("--cert", dest="cert_file_path", help="Path of JSON file for used certificates [optional]", metavar="FILEPATH")
    parser.add_option("--passphrase-in", dest="passphrase_in", help="Passphrase of input archive files [optional]", metavar="PASSPHRASE")


    (options, args) = parser.parse_args()

    if not options.env_file_path:
        parser.error("Environment archive option is missing!")
    if not options.pol_file_path:
        parser.error("Policy archive option is missing!")
    if not options.config_file_path:
        parser.error("Configuration file option is missing!")
 
    try:
        passphrase_in = ""
        if options.passphrase_in:
            passphrase_in = options.passphrase_in

        fed_config = FedConfigurator(options.pol_file_path, options.env_file_path, options.config_file_path, options.cert_file_path, passphrase_in)
        fed_config.update_templates()

    except Exception as e:
        if options.verbose:
            traceback.print_exc()
        else:
            print "ERROR: %r" % (e)
        sys.exit(1) 

    sys.exit(0)

if __name__ == "__main__":
    main()
