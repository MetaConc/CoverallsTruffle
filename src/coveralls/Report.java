package coveralls;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import gcov.Gcov;

public final class Report {

  private final String  repoToken;
  private final String  serviceName;
  private final boolean includeTravisData;

  private Report(final String repoToken) {
    includeTravisData = true;
    serviceName       = "travis-ci";
    this.repoToken    = repoToken;
  }

  public String generateCoverageJson(final Map<String, Long[]> coverageMap) throws FileNotFoundException {
    JSONObjectBuilder coverageRequest = JSONHelper.object();

    coverageRequest.add("repo_token",   repoToken);
    coverageRequest.add("service_name", serviceName);

    if (includeTravisData) {
      Map<String, String> env = System.getenv();
      coverageRequest.add("service_job_id", env.get("TRAVIS_JOB_ID"));
      coverageRequest.add("service_pull_request", env.get("TRAVIS_PULL_REQUEST"));
    }

    JSONArrayBuilder allSourceFiles = JSONHelper.array();

    for (String path : coverageMap.keySet()) {
      JSONObjectBuilder sourceFile = JSONHelper.object();

      File f = new File(path);
      if (!f.isFile()) { continue; }

      String currentDir = Paths.get(".").toAbsolutePath().
          normalize().toString();
      String absolutePath = f.getAbsolutePath();

      if (absolutePath.startsWith(currentDir)) {
        String relativePath = absolutePath.substring(
            currentDir.length());

        sourceFile.add("name", relativePath);
        sourceFile.add("source_digest", getMd5(new FileInputStream(f)));
        sourceFile.add("coverage", getArrayBuilder(coverageMap.get(path)));

        allSourceFiles.add(sourceFile);
      }
    }

    coverageRequest.add("source_files", allSourceFiles);
    return coverageRequest.toString();
  }

  private String getMd5(final InputStream code) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      DigestInputStream dis = new DigestInputStream(code, md);

      byte[] buffer = new byte[1024];
      int numRead;
      do {
        numRead = dis.read(buffer);

      }
      while (numRead != -1);

      byte[] result = md.digest();
      BigInteger bigInt = new BigInteger(1, result);
      String str = bigInt.toString(16);

      while (str.length() < 32) {
        str = "0" + str;
      }

      return str;

    } catch (NoSuchAlgorithmException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private JSONArrayBuilder getArrayBuilder(final Long[] values) {
    JSONArrayBuilder array = JSONHelper.array();

    for (Long l : values) {
      if (l != null) {
        array.add((int) (long) l);
      } else {
        array.add(l);
      }
    }

    return array;
  }

  private void sendRequestCoveralls(final String json) {
    String url = "https://coveralls.io/api/v1/jobs";
    try {
      MultipartUtility multipart = new MultipartUtility(url, "UTF-8");
      multipart.addFilePart("json_file", json, "application/json");

      multipart.finish();
    } catch (IOException ex) {
      // Checkstyle: stop
      System.err.println(ex);
      // Checkstyle: resume
    }
  }

  public static void main(final String[] args) {
    if (args.length < 2) {
      // Checkstyle: stop
      System.out.println("Usage: java Report repoToken gcovFile");
      // Checkstyle: resume
    }

    Report report = new Report(args[0]);
    try {
      FileInputStream in = new FileInputStream(args[1]);
      String result = report.generateCoverageJson(Gcov.load(in));
      report.sendRequestCoveralls(result);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
