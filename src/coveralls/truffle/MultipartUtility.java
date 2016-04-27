package coveralls.truffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * This utility class provides an abstraction layer for sending multipart HTTP
 * POST requests to a web server.
 *
 * @author www.codejava.net
 * @see http://www.codejava.net/java-se/networking/upload-files-by-sending-
 *      multipart-request-programmatically
 */
public class MultipartUtility {

  private final String        boundary;
  private static final String LINE_FEED = "\r\n";
  private final HttpURLConnection httpConn;
  private final OutputStream      outputStream;
  private final PrintWriter   writer;

  /**
   * This constructor initializes a new HTTP POST request with content type is
   * set to multipart/form-data.
   *
   * @param requestURL
   * @param charset
   * @throws IOException
   */
  public MultipartUtility(final String requestURL, final String charset)
      throws IOException {
    // creates a unique boundary based on time stamp
    boundary = "===" + System.currentTimeMillis() + "===";

    URL url = new URL(requestURL);
    httpConn = (HttpURLConnection) url.openConnection();
    httpConn.setUseCaches(false);
    httpConn.setDoOutput(true); // indicates POST method
    httpConn.setDoInput(true);
    httpConn.setRequestProperty("Content-Type",
        "multipart/form-data; boundary=" + boundary);
    httpConn.setRequestProperty("User-Agent", "Truffle Coverall.io Client");
    outputStream = httpConn.getOutputStream();
    writer = new PrintWriter(new OutputStreamWriter(outputStream, charset),
        true);
  }

  /**
   * Adds a upload file section to the request.
   *
   * @param fieldName
   *          name attribute in <input type="file" name="..." />
   * @param uploadFile
   *          a File to be uploaded
   * @throws IOException
   */
  public void addFilePart(final String fieldName, final String content,
      final String contentType)
      throws IOException {
    writer.append("--" + boundary).append(LINE_FEED);
    writer.append("Content-Disposition: form-data; name=\"" + fieldName
        + "\"; filename=\"" + fieldName + "\"").append(LINE_FEED);
    writer.append("Content-Type: " + contentType).append(LINE_FEED);
    writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
    writer.append(LINE_FEED);
    writer.flush();
    writer.append(content);
    writer.append(LINE_FEED);
    writer.flush();
  }

  /**
   * Completes the request and receives response from the server.
   *
   * @throws IOException
   */
  public void finish() throws IOException {
    writer.append(LINE_FEED).flush();
    writer.append("--" + boundary + "--").append(LINE_FEED);
    writer.close();

    // checks server's status code first
    int status = httpConn.getResponseCode();

    if (status == HttpURLConnection.HTTP_OK) {
      logResult(httpConn.getInputStream());
      httpConn.disconnect();
    } else {
      logResult(httpConn.getErrorStream());
      httpConn.disconnect();
      throw new IOException("Server returned non-OK status: " + status);
    }
  }

  private void logResult(final InputStream stream) throws IOException {
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(stream));
    String line = null;
    while ((line = reader.readLine()) != null) {
      // Checkstyle: stop
      System.out.println(line);
      // Checkstyle: resume
    }
    reader.close();
  }
}
