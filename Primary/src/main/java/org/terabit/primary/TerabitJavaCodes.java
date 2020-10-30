package org.terabit.primary;

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

//This code can not be compiled in kotlin compiler, so leave it in a java file.

public class TerabitJavaCodes {
    public static class KeyStoreOpResult{
        public int code = 0;
        public String desc = "";
        public KeyStore.PrivateKeyEntry entry = null;
    }

    public static void main(String[] args) throws Exception{


    }

    public static KeyStoreOpResult createSelfSignedCert(String jskFileName,String filePwd, String alias, String pwd) throws Exception
    {
        KeyStoreOpResult result = new KeyStoreOpResult();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        File jksFile = new File(jskFileName);
        if(jksFile.exists())
            try{
                keyStore.load(new FileInputStream(jksFile), filePwd.toCharArray());
            }catch (Exception e){
                result.code = 1;
                result.desc = e.getLocalizedMessage();
                return result;
            }
        else{
            keyStore.load(null, null);
        }

        if(keyStore.containsAlias(alias)){
            result.code = 2;
            result.desc = "Alias already exist";
            return result;
        }

        CertAndKeyGen keypair = new CertAndKeyGen("RSA", "SHA1WithRSA", null);
        X500Name x500Name = new X500Name("TERABIT", "DU", "DO", "DC", "DS", "DW");
        keypair.generate(2048);
        PrivateKey privKey = keypair.getPrivateKey();
        X509Certificate[] chain = new X509Certificate[1];
        chain[0] = keypair.getSelfCertificate(x500Name, new Date(), (long) 24 * 60 * 60);

        keyStore.setKeyEntry(alias, privKey, pwd.toCharArray(), chain);
        keyStore.store(new FileOutputStream(jskFileName), filePwd.toCharArray());
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias,
                new KeyStore.PasswordProtection(pwd.toCharArray()));
        result.code = 0;
        result.entry = entry;
        return result;
    }
}
