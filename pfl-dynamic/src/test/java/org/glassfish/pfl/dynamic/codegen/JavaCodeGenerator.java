/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.pfl.dynamic.codegen;

import org.glassfish.pfl.dynamic.codegen.impl.CodeGeneratorUtil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.glassfish.pfl.dynamic.codegen.spi.Wrapper._sourceCode;

public class JavaCodeGenerator extends CodeGeneratorBase {
    private File directory;
    private long sourceGenerationTime;
    private long compilationTime;
    private static final boolean CLEANUP_DIRECTORY = false;
    private static String dirName = "work." + System.currentTimeMillis();

    static String getDirName() {
        return dirName;
    }

    private void deleteFiles(File f) {
        if (f.isDirectory()) {
            for (File file : f.listFiles())
                deleteFiles(file);
        }

        if (!f.delete())
            throw new RuntimeException("Could not delete " + f);
    }

    private String pathName() {
        return className().replace('.', File.separatorChar);
    }

    private String containerName() {
        String pn = pathName();

        int len = pn.lastIndexOf(File.separatorChar);
        if (len == -1)
            return "";

        return pn.substring(0, len);
    }

    private String sourceName() {
        return pathName() + ".java";
    }

    private String classFileName() {
        return pathName() + ".class";
    }

    JavaCodeGenerator(ClassGeneratorFactory cgf) {
        super(cgf);

        File baseDir = new File("target/gen");
        directory = new File(baseDir, dirName);
        directory.mkdir(); // may fail because it already exists, which is OK.

        // Make sure the directory for the generated source
        // and compiled class file exists.
        String cn = containerName();
        File classDir;
        if (cn.equals(""))
            classDir = directory;
        else
            classDir = new File(directory, containerName());

        classDir.mkdirs(); // may fail because it already exists, which is OK.
    }

    /**
     * Compile the java source file for the named class in the
     * directory.  File are compiled in the same directory as
     * their source.
     */
    private int compileClass(File dir, String name) {
        String classpath = directory + System.getProperty("path.separator") + System.getProperty("java.class.path");

        String[] args = new String[]{"-g", "-classpath", classpath, new File(dir, name).toString()};

        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int result = compiler.run(null, null, null, args);
            if (result != 0)
                System.out.println("Compilation of class " + name + " failed with result " + result);
            return result;
        } catch (Exception exc) {
            System.out.println("Compilation of class " + name + " failed with exception " + exc);
            exc.printStackTrace();
            return 1;
        }
    }

    /**
     * Load the class file for the named class from the file.
     */
    private Class<?> loadClass(ClassLoader cl, File cfile,
                               String className) {
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(cfile);
            int fileSize = fis.available();
            byte[] cdata = new byte[fileSize];
            fis.read(cdata);
            return CodeGeneratorUtil.makeClass(className, cdata, null, cl);
        } catch (IOException exc) {
            throw new RuntimeException("Caught exception in loadClass", exc);
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (IOException exc) {
                    // IGNORE IOException on close
                }
        }
    }

    public Class generate(ClassLoader loader) {
        // System.out.println( System.getProperty( "user.dir" )) ;
        PrintStream ps = null;
        try {
            String str = sourceName();
            File out = new File(directory, str);
            ps = new PrintStream(out);

            long start = System.nanoTime();
            try {
                _sourceCode(ps, null);
            } finally {
                sourceGenerationTime = (System.nanoTime() - start) / 1000;
            }
        } catch (IOException exc) {
            exc.printStackTrace();
            throw new RuntimeException("Caught exception in generate", exc);
        } finally {
            if (ps != null)
                ps.close();
        }

        long start = System.nanoTime();
        try {
            if (compileClass(directory, sourceName()) != 0)
                throw new RuntimeException("Compilation of " + sourceName()
                        + " failed.");

            return loadClass(loader,
                    new File(directory, classFileName()), className());
        } finally {
            compilationTime = (System.nanoTime() - start) / 1000;

            if (CLEANUP_DIRECTORY)
                deleteFiles(directory);
        }
    }

    @SuppressWarnings("unused") // used by reflection
    public long sourceGenerationTime() {
        return sourceGenerationTime;
    }

    @SuppressWarnings("unused") // used by reflection
    public long compilationTime() {
        return compilationTime;
    }
}
