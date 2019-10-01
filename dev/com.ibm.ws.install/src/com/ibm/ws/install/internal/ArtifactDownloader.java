package com.ibm.ws.install.internal;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.ibm.ws.install.InstallException;
import com.ibm.ws.install.internal.InstallLogUtils.Messages;

public class ArtifactDownloader {

    private final int PROGRESS_CHUNK = 500000;

    private final int BUFFER_SIZE = 10000;

    private final String appName = "artifactDownloader"; //TODO replace with openliberty?

    private final String appVersion = "1.0.0"; //TODO replace with openliberty version?

    private final List<File> downloadedFiles = new ArrayList<File>();;

    public void synthesizeAndDownloadFeatures(List<String> mavenCoords, String dLocation, String repo) throws InstallException {
        checkValidProxy();
        configureProxyAuthentication();
        configureAuthentication();
        downloadedFiles.clear();
        int repoResponseCode = ArtifactDownloaderUtils.exists(repo);
        ArtifactDownloaderUtils.checkResponseCode(repoResponseCode, repo);
        List<String> featureURLs = ArtifactDownloaderUtils.acquireFeatureURLs(mavenCoords, repo);
        List<String> missingFeatures = ArtifactDownloaderUtils.getMissingFiles(featureURLs);
        if (!missingFeatures.isEmpty()) {
            List<String> missingFeatureList = new ArrayList<String>();
            for (String f : missingFeatures) {
                if (f.endsWith(".esa")) {
                    missingFeatureList.add(ArtifactDownloaderUtils.getFileNameFromURL(f));
                }
            }
            throw ExceptionUtils.createByKey("ERROR_FAILED_TO_DOWNLOAD_ASSETS_FROM_REPO", missingFeatureList, "feature(s)", repo);
        } else {
            for (String coords : mavenCoords) {
                synthesizeAndDownload(coords, "esa", dLocation, repo, false);
                synthesizeAndDownload(coords, "pom", dLocation, repo, false);
            }
        }
    }

    public void synthesizeAndDownload(String mavenCoords, String filetype, String dLocation, String repo, boolean individualDownload) throws InstallException {
        if (individualDownload) {
            checkValidProxy();
            int repoResponseCode = ArtifactDownloaderUtils.exists(repo);
            ArtifactDownloaderUtils.checkResponseCode(repoResponseCode, repo);
        }
        String groupId = ArtifactDownloaderUtils.getGroupId(mavenCoords).replace(".", "/") + "/";
        String artifactId = ArtifactDownloaderUtils.getartifactId(mavenCoords);
        String version = ArtifactDownloaderUtils.getVersion(mavenCoords);
        new File(dLocation + groupId + version + "/").mkdirs();

        String filename = ArtifactDownloaderUtils.getfilename(mavenCoords, filetype);
        String urlLocation = ArtifactDownloaderUtils.getUrlLocation(repo, groupId, artifactId, version, filename);
        String[] checksumFormats = new String[2];
        checksumFormats[0] = "MD5";
        checksumFormats[1] = "SHA1";
        configureProxyAuthentication();
        configureAuthentication();
        try {
            if (individualDownload && ArtifactDownloaderUtils.fileIsMissing(urlLocation)) {
                throw ExceptionUtils.createByKey("ERROR_FAILED_TO_DOWNLOAD_ASSETS_FROM_REPO", ArtifactDownloaderUtils.getFileNameFromURL(urlLocation), filetype + " file", repo);
            } else {
                download(urlLocation, dLocation, groupId, version, filename, checksumFormats);
            }
        } catch (IOException e) {
            throw ExceptionUtils.createByKey(e, "ERROR_INVALID_ESA", filename);
        }

    }

    private void download(String urlLocation, String dLocation, String groupId, String version, String filename, String[] checksumFormats) throws IOException, InstallException {
        try {
            URI uriLoc = new URI(urlLocation);
            File fileLoc = new File(ArtifactDownloaderUtils.getFileLocation(dLocation, groupId, version, filename));

            downloadInternal(uriLoc, fileLoc);
            downloadedFiles.add(fileLoc);

            for (String checksumFormat : checksumFormats) {
                String checksumLocal = ArtifactDownloaderUtils.getChecksum(fileLoc.getAbsolutePath(), checksumFormat);
                String checksumOrigin = ArtifactDownloaderUtils.getMasterChecksum(urlLocation, checksumFormat);
                if (!checksumLocal.equals(checksumOrigin)) {
                    ArtifactDownloaderUtils.deleteFiles(downloadedFiles, dLocation, groupId, version, filename);
                    downloadedFiles.clear();
                    throw ExceptionUtils.createByKey("ERROR_DOWNLOADED_ASSET_INVALID_CHECKSUM", filename, Messages.INSTALL_KERNEL_MESSAGES.getMessage("FEATURE_ASSET"));
                }
            }

        } catch (URISyntaxException e) {
            throw new InstallException(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new InstallException(e.getMessage());
        }
    }

    private void configureProxyAuthentication() {
        if (System.getProperty("http.proxyUser") != null) {
            Authenticator.setDefault(new SystemPropertiesProxyAuthenticator());
        }
    }

    private void configureAuthentication() {
        if (System.getProperty("MVNW_USERNAME") != null && System.getProperty("MVNW_PASSWORD") != null && System.getProperty("http.proxyUser") == null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(System.getProperty("MVNW_USERNAME"), System.getProperty("MVNW_PASSWORD").toCharArray());
                }
            });
        }
    }

    private void downloadInternal(URI address, File destination) throws IOException, InstallException {
        OutputStream out = null;
        URLConnection conn;
        InputStream in = null;
        try {
            URL url = address.toURL();
            System.out.println();
            try {
                out = new BufferedOutputStream(new FileOutputStream(destination));
            } catch (FileNotFoundException e) {
                throw ExceptionUtils.createByKey("ERROR_FAILED_TO_DOWNLOAD_FEATURE", ArtifactDownloaderUtils.getFileNameFromURL(address.toString()),
                                                 destination.toString());
            }
            if (System.getProperty("http.proxyUser") != null) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(System.getProperty("http.proxyHost"), 8080));
                conn = url.openConnection(proxy);
            } else {
                conn = url.openConnection();
            }
            addBasicAuthentication(address, conn);
            final String userAgentValue = calculateUserAgent();
            conn.setRequestProperty("User-Agent", userAgentValue);
            in = conn.getInputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int numRead;
            long progressCounter = 0;
            while ((numRead = in.read(buffer)) != -1) {
                progressCounter += numRead;
                if (progressCounter / PROGRESS_CHUNK > 0) {
                    progressCounter = progressCounter - PROGRESS_CHUNK;
                }
                out.write(buffer, 0, numRead);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private String calculateUserAgent() {

        String javaVendor = System.getProperty("java.vendor");
        String javaVersion = System.getProperty("java.version");
        String javaVendorVersion = System.getProperty("java.vm.version");
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        return String.format("%s/%s (%s;%s;%s) (%s;%s;%s)", appName, appVersion, osName, osVersion, osArch, javaVendor, javaVersion, javaVendorVersion);
    }

    private void addBasicAuthentication(URI address, URLConnection connection) throws IOException {
        String userInfo = calculateUserInfo(address);
        if (userInfo == null) {
            return;
        }
        connection.setRequestProperty("Authorization", "Basic " + base64Encode(userInfo));
    }

    private String base64Encode(String userInfo) {
        ClassLoader loader = getClass().getClassLoader();
        try {
            Method getEncoderMethod = loader.loadClass("java.util.Base64").getMethod("getEncoder");
            Method encodeMethod = loader.loadClass("java.util.Base64$Encoder").getMethod("encodeToString", byte[].class);
            Object encoder = getEncoderMethod.invoke(null);
            return (String) encodeMethod.invoke(encoder, new Object[] { userInfo.getBytes("UTF-8") });
        } catch (Exception java7OrEarlier) {
            try {
                Method encodeMethod = loader.loadClass("javax.xml.bind.DatatypeConverter").getMethod("printBase64Binary", byte[].class);
                return (String) encodeMethod.invoke(null, new Object[] { userInfo.getBytes("UTF-8") });
            } catch (Exception java5OrEarlier) {
                throw new RuntimeException("Downloading Maven distributions with HTTP Basic Authentication is not supported on your JVM.", java5OrEarlier);
            }
        }
    }

    private String calculateUserInfo(URI uri) {

        if (System.getProperty("MVNW_USERNAME") != null && System.getProperty("MVNW_PASSWORD") != null) {
            return System.getProperty("MVNW_USERNAME") + ':' + System.getProperty("MVNW_PASSWORD");
        }
        return uri.getUserInfo();
    }

    private static class SystemPropertiesProxyAuthenticator extends Authenticator {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(System.getProperty("http.proxyUser"), System.getProperty("http.proxyPassword", "").toCharArray());
        }
    }

    public List<File> getDownloadedEsas() {
        List<File> esaFiles = new ArrayList<File>();
        for (File f : downloadedFiles) {
            if (f.getName().endsWith(".esa")) {
                esaFiles.add(f);
            }
        }
        return esaFiles;
    }

    public List<File> getDownloadedPoms() {
        List<File> pomFiles = new ArrayList<File>();
        for (File f : downloadedFiles) {
            if (f.getName().endsWith(".pom")) {
                pomFiles.add(f);
            }
        }
        return pomFiles;
    }

    public List<File> getDownloadedFiles() {
        return downloadedFiles;
    }

    public void checkValidProxy() throws InstallException {
        String proxyPort = System.getProperty("http.proxyPort");
        if (System.getProperty("http.proxyUser") != null) {
            int proxyPortnum = Integer.parseInt(proxyPort);
            if (System.getProperty("http.proxyHost").isEmpty()) {
                throw ExceptionUtils.createByKey("ERROR_TOOL_PROXY_HOST_MISSING");
            } else if (proxyPortnum < 0 || proxyPortnum > 65535) {
                throw ExceptionUtils.createByKey("ERROR_TOOL_INVALID_PROXY_PORT", proxyPort);
            }
        }
    }

}
