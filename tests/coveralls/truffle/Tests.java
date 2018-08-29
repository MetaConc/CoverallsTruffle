package coveralls.truffle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.sl.SLLanguage;


public class Tests {

  private Context  context;
  private Coverage covInst;

  private static final String TEST_FILE = "test.sl";

  @Test
  public void checkCoverageMapForTestSLFile() throws IOException {
    InputStream testSlFile = getClass().getResourceAsStream("test.sl");
    Source testSl =
        Source.newBuilder("sl", new InputStreamReader(testSlFile), TEST_FILE).build();

    context.eval(testSl);

    Map<String, Long[]> result = covInst.getCoverageMap(new HashMap<>());

    assertTrue(result.containsKey(TEST_FILE));
    Long[] lines = result.get(TEST_FILE);

    assertArrayEquals(new Long[] {
        null, 0L, 0L, 0L, null, null,
        null, null, 20L, null, 120L, 100L, null, null, 20L, null, null,
        null, 1L, null, 21L, 20L, 20L, 20L, 0L, null, null, null}, lines);
  }

  @Before
  public void initSL() {
    context = Context.newBuilder(SLLanguage.ID).in(System.in).out(System.out)
                     .allowAllAccess(true).build();
    assertTrue("SimpleLanguage needs to be on the classpath for tests",
        context.getEngine().getLanguages().containsKey(SLLanguage.ID));

    covInst = Coverage.find(context.getEngine());
    assertNotNull("Coverage tool not found", covInst);
  }
}
