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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

@Registration(id = Coverage.ID)
public class Coverage extends TruffleInstrument {
  private Instrumenter instrumenter;
  public static final String                ID         = "coverageId";
  private String                repoToken;
  private String                serviceName;
  private final Map<SourceSection, Counter> statements = new HashMap<>();

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();
    setUpStatementInstrumentation();

    env.registerService(this);
  }

  private void setUpStatementInstrumentation() {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(StatementTag.class);

    instrumenter.attachFactory(filters.build(), ctx -> {
      Counter c;
      if (statements.containsKey(ctx.getInstrumentedSourceSection())) {
        c = statements.get(ctx.getInstrumentedSourceSection());
      } else {
        c = new Counter(ctx.getInstrumentedSourceSection());
        statements.put(ctx.getInstrumentedSourceSection(), c);
      }
      return new CountingNode(c);
    });
  }

  @Override
  protected void onDispose(final Env env) {
    String result = generateCoverageJson(getCoverageMap());
    sendRequestCoveralls(result);

    super.onDispose(env);
  }

  public Map<Source, Long[]> getCoverageMap() {
    Map<Source, Long[]> coverageMap = new HashMap<>();

    // cover executed lines
    for (Counter counter : statements.values()) {
      processCoverage(counter.getCounter(), counter.getSourceSection(), coverageMap);
    }

    // cover not executed lines
    List<SourceSection> sectionsNotExec = getCodeNotExecuted();
    for (SourceSection sourceSection : sectionsNotExec) {
      processCoverage(0, sourceSection, coverageMap);
    }

    return coverageMap;
  }

  private void processCoverage(final long counterVal,
      final SourceSection sourceSection, final Map<Source, Long[]> coverageMap) {
    Long[] array;
    Source src = sourceSection.getSource();
    if (coverageMap.containsKey(src)) {
      array = coverageMap.get(src);
    } else if (src.getLineCount() == 0) {
      return;
    } else {
      array = new Long[src.getLineCount()];
      coverageMap.put(src, array);
    }

    int line = sourceSection.getStartLine() - 1;
    if (array[line] == null) {
      array[line] = counterVal;
    } else {
      array[line] = Math.max(counterVal, array[line]);
    }
  }

  public String generateCoverageJson(final Map<Source, Long[]> coverageMap) {
    JSONObjectBuilder coverageRequest = JSONHelper.object();

    coverageRequest.add("repo_token", getRepoToken());
    coverageRequest.add("service-name", getServiceName());

    JSONArrayBuilder allSourceFiles = JSONHelper.array();

    for (Source s : coverageMap.keySet()) {
      JSONObjectBuilder sourceFile = JSONHelper.object();

      File f = new File(s.getName());

      if (f.isFile()) {

        String currentDir = Paths.get(".").toAbsolutePath().
            normalize().toString();
        String absolutePath = f.getAbsolutePath();

        if (absolutePath.startsWith(currentDir)) {
          String relativePath = absolutePath.substring(
              currentDir.length());

          sourceFile.add("name", relativePath);
          sourceFile.add("source_digest", getEncryptedCode(s.getInputStream()));
          sourceFile.add("coverage", getArrayBuilder(coverageMap.get(s)));

          allSourceFiles.add(sourceFile);
        }
      }
    }

    coverageRequest.add("source_files", allSourceFiles);
    return coverageRequest.toString();
  }

  private String getEncryptedCode(final InputStream code) {
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

    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return null;
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

  public List<SourceSection> getCodeNotExecuted() {
    Collection<RootCallTarget> collection = Truffle.getRuntime().getCallTargets();
    List<SourceSection> allSourceSections = new ArrayList<>();

    for (RootCallTarget  rootCallTarget: collection) {
      RootNode root = rootCallTarget.getRootNode(); // AST node
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

  public String getRepoToken() {
    return repoToken;
  }


  public void setRepoToken(final String repoToken) {
    this.repoToken = repoToken;
  }


  public String getServiceName() {
    return serviceName;
  }


  public void setServiceName(final String serviceName) {
    this.serviceName = serviceName;
  }
}
