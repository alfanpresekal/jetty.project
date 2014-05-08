//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.start;

import static org.hamcrest.Matchers.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.start.Props.Prop;
import org.eclipse.jetty.start.config.ConfigSource;
import org.eclipse.jetty.start.config.ConfigSources;
import org.eclipse.jetty.start.config.DirConfigSource;
import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.TestingDir;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ExtraStartTest
{
    private static class MainResult
    {
        private Main main;
        private StartArgs args;

        public void assertSearchOrder(List<String> expectedSearchOrder)
        {
            ConfigSources sources = main.getBaseHome().getConfigSources();
            List<String> actualOrder = new ArrayList<>();
            for (ConfigSource source : sources)
            {
                if (source instanceof DirConfigSource)
                {
                    actualOrder.add(source.getId());
                }
            }
            ConfigurationAssert.assertOrdered("Search Order",expectedSearchOrder,actualOrder);
        }

        public void assertProperty(String key, String expectedValue)
        {
            Prop prop = args.getProperties().getProp(key);
            String prefix = "Prop[" + key + "]";
            Assert.assertThat(prefix + " should have a value",prop,notNullValue());
            Assert.assertThat(prefix + " value",prop.value,is(expectedValue));
        }
    }

    @Rule
    public TestingDir testdir = new TestingDir();

    private MainResult runMain(File baseDir, File homeDir, String... cmdLineArgs) throws Exception
    {
        MainResult ret = new MainResult();
        ret.main = new Main();
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add("jetty.home=" + homeDir.getAbsolutePath());
        cmdLine.add("jetty.base=" + baseDir.getAbsolutePath());
        // cmdLine.add("--debug");
        for (String arg : cmdLineArgs)
        {
            cmdLine.add(arg);
        }
        ret.args = ret.main.processCommandLine(cmdLine);
        return ret;
    }

    @Test
    public void testNoExtras() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1");

        // Simple command line - no reference to extra-start-dirs
        MainResult result = runMain(base,home);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
    }

    @Test
    public void testCommandLine_1Extra() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini","jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1");

        // Simple command line reference to extra-start-dir
        MainResult result = runMain(base,home,
        // direct reference via path
                "--extra-start-dir=" + common.getAbsolutePath());

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(common.getAbsolutePath());
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testCommandLine_1Extra_FromSimpleProp() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini","jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1");

        // Simple command line reference to extra-start-dir via property (also on command line)
        MainResult result = runMain(base,home,
        // property
                "my.common=" + common.getAbsolutePath(),
                // reference via property
                "--extra-start-dir=${my.common}");

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add("${my.common}"); // should see property use
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testCommandLine_1Extra_FromPropPrefix() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create opt
        File opt = testdir.getFile("opt");
        FS.ensureEmpty(opt);

        // Create common
        File common = new File(opt,"common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini","jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1");

        String dirRef = "${my.opt}" + File.separator + "common";

        // Simple command line reference to extra-start-dir via property (also on command line)
        MainResult result = runMain(base,home,
        // property to 'opt' dir
                "my.opt=" + opt.getAbsolutePath(),
                // reference via property prefix
                "--extra-start-dir=" + dirRef);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(dirRef); // should use property
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testCommandLine_1Extra_FromCompoundProp() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create opt
        File opt = testdir.getFile("opt");
        FS.ensureEmpty(opt);

        // Create common
        File common = new File(opt,"common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini","jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1");

        String dirRef = "${my.opt}" + File.separator + "${my.dir}";

        // Simple command line reference to extra-start-dir via property (also on command line)
        MainResult result = runMain(base,home,
        // property to 'opt' dir
                "my.opt=" + opt.getAbsolutePath(),
                // property to commmon dir name
                "my.dir=common",
                // reference via property prefix
                "--extra-start-dir=" + dirRef);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(dirRef); // should use property
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testRefCommon() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini","jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "--extra-start-dir=" + common.getAbsolutePath());

        MainResult result = runMain(base,home);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(common.getAbsolutePath());
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testRefCommonAndCorp() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini","jetty.port=8080");

        // Create corp
        File corp = testdir.getFile("corp");
        FS.ensureEmpty(corp);

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "--extra-start-dir=" + common.getAbsolutePath(), //
                "--extra-start-dir=" + corp.getAbsolutePath());

        MainResult result = runMain(base,home);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(common.getAbsolutePath());
        expectedSearchOrder.add(corp.getAbsolutePath());
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testRefCommonRefCorp() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create corp
        File corp = testdir.getFile("corp");
        FS.ensureEmpty(corp);
        TestEnv.makeFile(corp,"start.ini","jetty.port=9090");

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini", //
                "--extra-start-dir=" + corp.getAbsolutePath(), //
                "jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "--extra-start-dir=" + common.getAbsolutePath());

        MainResult result = runMain(base,home);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(common.getAbsolutePath());
        expectedSearchOrder.add(corp.getAbsolutePath());
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testRefCommonRefCorp_FromSimpleProps() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create corp
        File corp = testdir.getFile("corp");
        FS.ensureEmpty(corp);
        TestEnv.makeFile(corp,"start.ini", //
                "jetty.port=9090");

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini", //
                "my.corp=" + corp.getAbsolutePath(), //
                "--extra-start-dir=${my.corp}", //
                "jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "my.common=" + common.getAbsolutePath(), //
                "--extra-start-dir=${my.common}");

        MainResult result = runMain(base,home);

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add("${my.common}");
        expectedSearchOrder.add("${my.corp}");
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","8080"); // from 'common'
    }

    @Test
    public void testRefCommonRefCorp_CmdLineRef() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create devops
        File devops = testdir.getFile("devops");
        FS.ensureEmpty(devops);
        TestEnv.makeFile(devops,"start.ini", //
                "--module=logging", //
                "jetty.port=2222");

        // Create corp
        File corp = testdir.getFile("corp");
        FS.ensureEmpty(corp);
        TestEnv.makeFile(corp,"start.ini", //
                "jetty.port=9090");

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini", //
                "--extra-start-dir=" + corp.getAbsolutePath(), //
                "jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "--extra-start-dir=" + common.getAbsolutePath());

        MainResult result = runMain(base,home,
        // command line provided extra-start-dir ref
                "--extra-start-dir=" + devops.getAbsolutePath());

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(devops.getAbsolutePath());
        expectedSearchOrder.add(common.getAbsolutePath());
        expectedSearchOrder.add(corp.getAbsolutePath());
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","2222"); // from 'devops'
    }

    @Test
    public void testRefCommonRefCorp_CmdLineProp() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create corp
        File corp = testdir.getFile("corp");
        FS.ensureEmpty(corp);
        TestEnv.makeFile(corp,"start.ini", //
                "jetty.port=9090");

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);
        TestEnv.makeFile(common,"start.ini", //
                "--extra-start-dir=" + corp.getAbsolutePath(), //
                "jetty.port=8080");

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "--extra-start-dir=" + common.getAbsolutePath());

        MainResult result = runMain(base,home,
        // command line property should override all others
                "jetty.port=7070");

        List<String> expectedSearchOrder = new ArrayList<>();
        expectedSearchOrder.add("${jetty.base}");
        expectedSearchOrder.add(common.getAbsolutePath());
        expectedSearchOrder.add(corp.getAbsolutePath());
        expectedSearchOrder.add("${jetty.home}");
        result.assertSearchOrder(expectedSearchOrder);

        result.assertProperty("jetty.host","127.0.0.1");
        result.assertProperty("jetty.port","7070"); // from command line
    }

    @Test
    public void testBadDoubleRef() throws Exception
    {
        // Create home
        File home = testdir.getFile("home");
        FS.ensureEmpty(home);
        TestEnv.copyTestDir("usecases/home",home);

        // Create common
        File common = testdir.getFile("common");
        FS.ensureEmpty(common);

        // Create corp
        File corp = testdir.getFile("corp");
        FS.ensureEmpty(corp);
        TestEnv.makeFile(corp,"start.ini",
        // standard property
                "jetty.port=9090",
                // INTENTIONAL BAD Reference (duplicate)
                "--extra-start-dir=" + common.getAbsolutePath());

        // Populate common
        TestEnv.makeFile(common,"start.ini",
        // standard property
                "jetty.port=8080",
                // reference to corp
                "--extra-start-dir=" + corp.getAbsolutePath());

        // Create base
        File base = testdir.getFile("base");
        FS.ensureEmpty(base);
        TestEnv.makeFile(base,"start.ini", //
                "jetty.host=127.0.0.1",//
                "--extra-start-dir=" + common.getAbsolutePath());

        try
        {
            runMain(base,home);
            Assert.fail("Should have thrown a UsageException");
        }
        catch (UsageException e)
        {
            Assert.assertThat("UsageException",e.getMessage(),containsString("Duplicate"));
        }
    }
}