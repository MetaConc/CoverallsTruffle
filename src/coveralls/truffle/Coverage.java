/**
 * Copyright (c) 2016 Carmen Torres López, Stefan Marr
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package coveralls.truffle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import gcov.Gcov;


@Registration(name = "CoverallsTruffle", id = Coverage.ID, version = "0.1",
    services = {Coverage.class})
public class Coverage extends TruffleInstrument {
  static final String ID = "coverageId";

  public static Coverage find(final Engine engine) {
    Instrument instrument = engine.getInstruments().get(ID);
    if (instrument == null) {
      throw new IllegalStateException(
          "Coverage tool not properly installed into polyglot.Engine");
    }

    return instrument.lookup(Coverage.class);
  }

  private final Map<SourceSection, Counter> statements = new HashMap<>();
  private final Set<RootNode>               rootNodes  = new HashSet<>();

  private Instrumenter instrumenter;

  private String              file;
  private Map<String, Long[]> coverage;

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();
    setUpStatementInstrumentation();

    env.registerService(this);
  }

  private void setUpStatementInstrumentation() {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(StatementTag.class);

    instrumenter.attachExecutionEventFactory(filters.build(), ctx -> {
      Counter c;
      if (statements.containsKey(ctx.getInstrumentedSourceSection())) {
        c = statements.get(ctx.getInstrumentedSourceSection());
      } else {
        c = new Counter(ctx.getInstrumentedSourceSection());
        statements.put(ctx.getInstrumentedSourceSection(), c);
      }
      return new CountingNode(c);
    });

    instrumenter.attachLoadSourceSectionListener(
        SourceSectionFilter.newBuilder().build(),
        (final LoadSourceSectionEvent event) -> {
          rootNodes.add(event.getNode().getRootNode());
        },
        true);
  }

  @Override
  protected void onDispose(final Env env) {
    if (file == null) {
      return;
    }

    try {
      Map<String, Long[]> map = getCoverageMap(coverage);
      try (FileWriter writter = new FileWriter(file)) {
        writter.write(Gcov.toString(map));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, Long[]> getCoverageMap(final Map<String, Long[]> oldData) {
    Map<String, Long[]> coverageMap = new HashMap<>();

    // cover executed lines
    for (Counter counter : statements.values()) {
      processCoverage(counter.getCounter(), counter.getSourceSection(), coverageMap);
    }

    // cover not executed lines
    List<SourceSection> sectionsNotExec = getCodeNotExecuted();
    for (SourceSection sourceSection : sectionsNotExec) {
      processCoverage(0, sourceSection, coverageMap);
    }

    // merge with old data
    for (Entry<String, Long[]> e : oldData.entrySet()) {
      Long[] data = coverageMap.get(e.getKey());
      Long[] oldD = e.getValue();
      if (data != null) {
        assert oldD.length <= data.length : "The gcov data format doesn't make lines explicit that don't have code";
        for (int i = 0; i < oldD.length; i += 1) {
          if (oldD[i] != null) {
            updateLine(data, i, oldD[i]);
          }
        }
      } else {
        assert oldD != null;
        coverageMap.put(e.getKey(), oldD);
      }
    }

    return coverageMap;
  }

  private void processCoverage(final long counterVal,
      final SourceSection sourceSection, final Map<String, Long[]> coverageMap) {
    Long[] array;
    Source s = sourceSection.getSource();
    String path = s.getPath() != null ? s.getPath() : s.getName();

    if (coverageMap.containsKey(path)) {
      array = coverageMap.get(path);
    } else if (s.getLineCount() == 0) {
      return;
    } else {
      array = new Long[s.getLineCount()];
      coverageMap.put(path, array);
    }

    int line = sourceSection.getStartLine() - 1;
    updateLine(array, line, counterVal);
  }

  private static void updateLine(final Long[] arr, final int line, final long cntVal) {
    if (arr[line] == null) {
      arr[line] = cntVal;
    } else {
      arr[line] = Math.max(cntVal, arr[line]);
    }
  }

  public List<SourceSection> getCodeNotExecuted() {
    List<SourceSection> allSourceSections = new ArrayList<>();

    for (RootNode root : rootNodes) {
      Map<SourceSection, Set<Class<?>>> sourceSectionsAndTags = new HashMap<>();

      root.accept(node -> {
        Set<Class<?>> tags = instrumenter.queryTags(node);

        if (tags.contains(StatementTag.class)) {
          if (sourceSectionsAndTags.containsKey(node.getSourceSection())) {
            sourceSectionsAndTags.get(node.getSourceSection()).addAll(tags);
          } else {
            sourceSectionsAndTags.put(node.getSourceSection(),
                new HashSet<>(tags));
          }
        }
        return true;
      });

      allSourceSections.addAll(sourceSectionsAndTags.keySet());
    }

    return allSourceSections;
  }

  /**
   * @param file, i.e., path to the file
   */
  public void setOutputFile(final String file) throws FileNotFoundException, IOException {
    this.file = file;
    File f = new File(file);
    if (f.exists()) {
      coverage = Gcov.load(new FileInputStream(f));
    } else {
      coverage = new HashMap<>();
    }
  }
}
