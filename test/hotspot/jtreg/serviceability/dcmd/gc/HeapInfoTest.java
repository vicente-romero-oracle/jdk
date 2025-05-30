/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import org.testng.annotations.Test;
import org.testng.Assert;

import java.io.IOException;

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;


/*
 * @test
 * @summary Test of diagnostic command GC.heap_info
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.xml
 *          java.management
 * @run testng HeapInfoTest
 */
public class HeapInfoTest {
    public void run(CommandExecutor executor) {
        String cmd = "GC.heap_info";
        OutputAnalyzer output = executor.execute(cmd);

        // All GCs have different strategies for printing similar information,
        // which makes it hard to grep for anything substantial. However, all
        // GCs print the string "used", so lets check for that to see if the
        // jcmd printed something at all.
        output.shouldContain("used");

        output.shouldNotContain("Unknown diagnostic command");
        output.shouldHaveExitValue(0);
    }

    @Test
    public void pid() {
        run(new PidJcmdExecutor());
    }
}

