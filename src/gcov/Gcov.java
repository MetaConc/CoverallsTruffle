package gcov;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Gcov {

  private static final String FILE_MARK = "file:";
  private static final String LINE_MARK = "lcount:";

  /**
   * Load a Gcov file with coverage data and return it as a map.
   */
  public static Map<String, Long[]> load(final InputStream stream) throws IOException {
    InputStreamReader in = new InputStreamReader(stream);
    BufferedReader reader = new BufferedReader(in);

    Map<String, Long[]> result = new HashMap<>();

    String line;

    String fileName = null;
    Map<Long, Long> coverage = null;

    while ((line = reader.readLine()) != null) {
      if (line.startsWith(FILE_MARK)) {
        if (coverage != null) {
          addCoverageEntry(result, fileName, coverage);
        }

        fileName = line.substring(FILE_MARK.length());
        coverage = new HashMap<>();
      } else if (line.startsWith(LINE_MARK)) {
        addLineData(line, coverage);
      } else {
        throw new IOException("Line contains data that is not yet supported: " + line);
      }
    }

    addCoverageEntry(result, fileName, coverage);
    return result;
  }

  public static String toString(final Map<String, Long[]> coverageMap) {
    StringBuilder builder = new StringBuilder();
    for (Entry<String, Long[]> e : coverageMap.entrySet()) {
      builder.append(FILE_MARK);
      builder.append(e.getKey());
      builder.append('\n');

      Long[] lines = e.getValue();
      for (int i = 0; i < lines.length; i += 1) {
        if (lines[i] != null) {
          builder.append(LINE_MARK);
          builder.append(i + 1);
          builder.append(',');
          builder.append(lines[i]);
          builder.append('\n');
        }
      }
    }

    return builder.toString();
  }

  private static void addLineData(final String line, final Map<Long, Long> coverage) {
    String data = line.substring(LINE_MARK.length());
    String[] lineData = data.split(",");
    assert lineData.length == 2;
    coverage.put(Long.valueOf(lineData[0]), Long.valueOf(lineData[1]));
  }

  private static void addCoverageEntry(final Map<String, Long[]> result,
      final String fileName, final Map<Long, Long> coverage) {
    int numLines = (int) (long) coverage.keySet().stream().reduce(Long::max).get();
    Long[] lines = new Long[numLines];
    for (Entry<Long, Long> e : coverage.entrySet()) {
      lines[(int) (long) e.getKey() - 1] = e.getValue();
    }
    result.put(fileName, lines);
  }
}
