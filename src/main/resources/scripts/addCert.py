# -----------------------------------------------------------------------------
# Utility methods for Cert Store manipulation
# -----------------------------------------------------------------------------

from java.io import FileInputStream, FileReader
from java.lang import String
from java.security import KeyFactory, KeyStore
from java.security.spec import PKCS8EncodedKeySpec
from esapi import EntityStoreAPI
from com.vordel.trace import Trace
from com.vordel.security.openssl import PKCS12
from org.bouncycastle.util.io.pem import PemReader as PEMReader
from org.bouncycastle.openssl import PasswordFinder

# -----------------------------------------------------------------------------
# Password finder class
# -----------------------------------------------------------------------------
class MyPasswordFinder(PasswordFinder):
    
    password = None
    
    def __init__(self, password):
        self.password = password
        
    def getPassword(self):
        return String(self.password).toCharArray()
        
# -----------------------------------------------------------------------------
# Class to define utility methods to add gets Certificates and keys from 
# different resources and add them to the Cert Store.
# -----------------------------------------------------------------------------
class CertStoreUtil(object):
    
    # -------------------------------------------------------------------------
    # Utility method to add a Certificate to the Cert Store
    # -------------------------------------------------------------------------
    @classmethod
    def addCertToStore(cls, es, alias, cert, privateKey=None):
        # Gets the Cert store using short hand key
        certStore = es.get('/[Certificates]name=Certificate Store')
        escapedAlias = es.escapeField(alias)
        shk = '[Certificate]dname=' + escapedAlias
        
        # See if the certificate alias already exists in the entity store, 
        # if it does then update it thereby preserving any references to any HTTPS interfaces that are using this cert
        certEntity = es.getChild(certStore, shk)
        
        if certEntity != None:
            # Updates the existing certificate in the certstore
            certEntity.setBinaryValue("content", cert.getEncoded())
            if privateKey != None:
                encrypted = es.encryptBytes(privateKey.getEncoded())
                certEntity.setStringField("key", encrypted) 
            else:
                # no private key specified - check if there was a private key in the earlier instance in the entity store - if so, remove it
                entityPKey = certEntity.getStringValue ("key")
                
                if entityPKey is not None and len (entityPKey) > 0:
                    Trace.info ("No private key specified but one existed in the earlier instance so deleting it")
                    certEntity.removeField ("key")
                
            es.updateEntity(certEntity)
            Trace.info("Updated cert with alias: " + alias + " and subject: " + cert.getSubjectDN().getName())
            return
        else:
            # Adds the certificate to the certstore 

            certEntity = es.createEntity("Certificate")
            certEntity.setStringField("dname", alias)
            certEntity.setBinaryValue("content", cert.getEncoded())
            if privateKey != None:
                encrypted = es.encryptBytes(privateKey.getEncoded())
                certEntity.setStringField("key", encrypted)
            es.addEntity(certStore, certEntity)
            Trace.info("Added a cert with alias: " + alias + " and subject: " + cert.getSubjectDN().getName())

    # -------------------------------------------------------------------------
    # Utility method to get Key from P12 file. 
    # -------------------------------------------------------------------------
    @classmethod 
    def getKeyFromP12(cls, file, password=None):
        io = FileInputStream(file)
        pkcs12 = PKCS12(io)
        if password != None:
            pkcs12.decrypt(String(password).toCharArray())
        key = pkcs12.getKey()
        Trace.info("Loaded key with algorithm: " + key.getAlgorithm() + " and format: " + key.getFormat())
        return key

    # -------------------------------------------------------------------------
    # Utility method to get Certificate from P12 file.
    # -------------------------------------------------------------------------
    @classmethod 
    def getCertFromP12(cls, file, password):
        # Get the Key store
        ks = KeyStore.getInstance("PKCS12")
        io = FileInputStream(file)
        # Loads the KeyStore from the file input stream and uses the password 
        # to unlock the keystore, or to check the integrity of the keystore data. 
        # If the password is not given for integrity checking, then 
        # integrity checking is not performed.         
        ks.load(io, String(password).toCharArray())
        io.close()

        # Lists all the alias names of this keystore. 
        aliases = ks.aliases()

        # Returns the first Certificate that matches an alias name
        while (aliases.hasMoreElements()): 
            alias = aliases.nextElement()
            if (ks.isKeyEntry(alias)):
                return ks.getCertificate(alias)        
        return None
        
    # -------------------------------------------------------------------------
    # Utility method to get private key
    # -------------------------------------------------------------------------
    @classmethod 
    def getKeyFromPEM(cls, file, password=None):
        io = FileInputStream(file)
        # PEMReader is used for reading OpenSSL PEM encoded streams containing 
        # X509 certificates, PKCS8 encoded keys and PKCS7 objects. 
        if password == None:
            reader = PEMReader(FileReader(file))
        else:
            reader = PEMReader(FileReader(file), MyPasswordFinder(password))
        keyPair = reader.readPemObject().readContent()
        if keyPair != None:
            kf = KeyFactory.getInstance("RSA")
            return kf.generatePrivate(PKCS8EncodedKeySpec(pemContent))
        return None
        
