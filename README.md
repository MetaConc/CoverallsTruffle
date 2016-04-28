# CoverallsTruffle: Language Agnostic Coverage Tracking for Truffle

Truffle is an open-source framework for the implementation of high-performance
language runtimes using Java and the Java Virtual Machine (JVM).
[Coveralls.io][1] is a web service to help you track
code coverage, for instance to ensure that new code is covered by tests.

CoverallsTruffle uses the Truffle Instrumentation framework to collect coverage
information, i.e., executed and not executed lines, and send it to
[Coveralls.io][1] to evaluate the coverage.

# How To Use

To use Coveralls.io, you'll need an account and register the relevant project
repositories. Coveralls.io does not store source code itself, but accesses for
instance GitHub or BitBucket.

To report results, Coveralls.io assigns each project a `repo_token` that needs
to be passed to CoverallsTruffle.

CoverallsTruffle can be used similar to other Truffle instruments by enabling it
when configuring the [`PolyglotEngine`][2]. It needs to be enabled and configure
with the custom repository token and optionally a service name.
For details see the [Coveralls documentation][3].

```Java
PolyglotEngine engine = PolyglotEngine.newBuilder().build();
Instrument covInst = engine.getInstruments().get(Coverage.ID);
covInst.setEnabled(true); // enable the instrument

// configure coverage instrument
Coverage coverage = engineInst.lookup(Coverage.class);
coverage.setRepoToken("YOUR_TOKEN");  // Needs to be updated
coverage.setServiceName("travis-ci"); // Set the service_name, see Coveralls documentation

// start execution as usual.
```

As a further example, see [`Tests`][4], which uses the SimpleLanguage to check
that the coverage is determined correctly.

# Implementation Overview

The main classes are:

`Coverage`:
 - `onCreate(.)` registers instrumentation for `StatementTag`.
 - `onDispose(.)` processes the data from the instrumentation to determine the
    executed lines of code from the AST and sends the results as JSON to Coveralls.io

`CountingNode`: instruments AST nodes with the `StatementTag` and increments a
counter each time the AST node is executed.

`Counter`: encapsulates the value of the counter and the source section to which is related.

[1]: https://coveralls.io/
[2]: https://github.com/graalvm/truffle/blob/master/truffle/com.oracle.truffle.api.vm/src/com/oracle/truffle/api/vm/PolyglotEngine.java#L65
[3]: https://coveralls.zendesk.com/hc/en-us/articles/201350799-API-Reference
[4]: https://github.com/MetaConc/CoverallsTruffle/blob/master/tests/coveralls/truffle/Tests.java#L28
