package gcov;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.Test;


public class GcovTests {

  @Test
  public void loadGcovFile() throws IOException {
    InputStream gcovFile = getClass().getResourceAsStream("test.gcov");
    Map<String, Long[]> lineCoverage = Gcov.load(gcovFile);

    Long[] lines = lineCoverage.get("test.sl");
    assertNotNull(lines);
    assertArrayEquals(new Long[] {
        null, 0L, 0L, 0L, null, null,
        null, null, 20L, null, 120L, 100L, null, null, 20L, null, null,
        null, 1L, null, 21L, 20L, 20L, 20L, 0L}, lines);
  }
}
