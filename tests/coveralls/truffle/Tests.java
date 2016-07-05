package coveralls.truffle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;


public class Tests {

  private PolyglotEngine engine;
  private Instrument     engineInst;
  private Coverage       covInst;

  @Test
  public void checkCoverageMapForTestSLFile() throws IOException {
    InputStream testSlFile = getClass().getResourceAsStream("test.sl");
    Source testSl = Source.newBuilder(new InputStreamReader(testSlFile)).
        name("test.sl").mimeType("application/x-sl").
        build();

    engine.eval(testSl);

    Map<Source, Long[]> result = covInst.getCoverageMap();
    assertTrue(result.containsKey(testSl));
    Long[] lines = result.get(testSl);

    assertArrayEquals(new Long[] {
        null, 0L, 0L, 0L, null, null,
        null, null, 20L, null, 120L, 100L, null, null, 20L, null, null,
        null, 1L, null, 21L, 20L, 20L, 20L, 0L, null, null, null}, lines);
  }

  @Before
  public void initSL() {
    engine = PolyglotEngine.newBuilder().build();
    assertTrue("SimpleLanguage needs to be on the classpath for tests",
        engine.getLanguages().containsKey("application/x-sl"));

    engineInst = engine.getInstruments().get(Coverage.ID);
    assertNotNull("Coverage tool not found", engineInst);
    engineInst.setEnabled(true);

    covInst = engineInst.lookup(Coverage.class);

    assertNotNull("Coverage object/service not found", covInst);
  }
}
