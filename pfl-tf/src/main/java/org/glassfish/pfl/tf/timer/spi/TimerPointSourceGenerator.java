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

package org.glassfish.pfl.tf.timer.spi ;

import org.glassfish.pfl.dynamic.codegen.impl.ASMUtil;
import org.glassfish.pfl.dynamic.codegen.spi.Expression;
import org.glassfish.pfl.dynamic.codegen.spi.ImportList;
import org.glassfish.pfl.dynamic.codegen.spi.Type;
import org.glassfish.pfl.basic.contain.Pair;
import java.io.File ;
import java.io.PrintStream ;

import java.util.List ;
import java.util.ArrayList ;
import java.util.Properties ;
import java.util.Collections;
import java.util.Comparator;

import java.io.IOException ;

import static java.lang.reflect.Modifier.* ;

import static org.glassfish.pfl.dynamic.codegen.spi.Wrapper.* ;

/** Used to generate a class that represents all Timers found in
 * a TF source file.
 * Uses the codegen library to generate the source file.
 */
public class TimerPointSourceGenerator {
    private static final Comparator<Named> COMP = new Comparator<Named>() {
        @Override
        public int compare(Named o1, Named o2) {
            return o1.name().compareTo( o2.name() ) ;
        }
    } ;

    private static Type TimerFactory ; 
    private static Type Timer ; 
    private static Type TimerEventController ;
    private static Type TimerGroup ; 
    private static ImportList standardImports ;

    static {
	_clear() ;
	_package() ;

	TimerFactory = _import( TimerFactory.class.getName() ) ;
	Timer = _import( Timer.class.getName() ) ;
	TimerEventController = _import( TimerEventController.class.getName() );
	TimerGroup = _import( TimerGroup.class.getName() ) ;

	standardImports = _import() ;
    }

    /** Generate a single class which contains:
     * <ul>
     * <li>private fields for Timers and TimerGroups
     * <li>public accessor methods for Timers and TimerGroups
     * <li>A public constructor <init>(TimerFactory) that initializes the
     * Timer and TimerGroup fields, and the TimerGroup containment.
     * </ul>
     * @param dirName
     * @param description
     * @throws IOException
     */
    public static void generateFile( String dirName,
        Pair<String,TimerFactory> description ) throws IOException {
    
	String packageName = description.first() ;
	TimerFactory tf = description.second() ;
        generateSingleClass( dirName, packageName, tf ) ;
    }

    private static void generateSingleClass( String dirName,
        String packageName, TimerFactory tf ) throws IOException {

	startFile( packageName ) ;
	_class( PUBLIC, tf.name(), _Object() ) ;

        generateFields( tf, false, true ) ;
	generateConstructor( tf, false ) ;
	generateAccessorMethods( tf, true ) ;

	_end() ;

	Type type = Type._classGenerator( _classGenerator() ) ;

	writeFile( dirName, type ) ;
    }

    private static void startFile( String packageName ) {
	_clear() ;
	_package( packageName ) ;
	_import( standardImports ) ;
    }

    private static void generateFields( TimerFactory tf, 
        boolean generateController, boolean privateTimers ) {

        if (generateController) {
            _data( PROTECTED|FINAL, TimerEventController, "controller" ) ;
        }

        final int mod = FINAL | (privateTimers ? PRIVATE : PROTECTED) ;

        final List<Timer> timers = 
            new ArrayList<Timer>( tf.timers().values() ) ;
        Collections.sort( timers, COMP );
	for (Timer t : timers) {
	    _data( mod, Timer, t.name() ) ;
	}

        final List<TimerGroup> timerGroups = 
            new ArrayList<TimerGroup>( tf.timerGroups().values() ) ;
        Collections.sort( timerGroups, COMP ) ;
	for (TimerGroup tg : timerGroups) {
	    _data( PRIVATE|FINAL, TimerGroup, tg.name() ) ;
	}
    }

    private static void generateConstructor( TimerFactory tf, 
        boolean generateController ) {

        Expression controller = null ;

	_constructor( PUBLIC ) ;
	    Expression tfe = _arg( TimerFactory, "tf" ) ;
            if (generateController) {
                controller = _arg( TimerEventController,
                    "controller" ) ;
            }
	_body() ;

        if (generateController) {
            _assign( _field( _this(), "controller" ), controller ) ;
        }

	// create all timers
	for (Timer t : tf.timers().values() ) {
	    _assign( _v( t.name() ), 
		_call( tfe, "makeTimer", 
		    _const(t.name()), _const(t.description()))) ;
	}
    
	// create all timer groups
	for (TimerGroup tg : tf.timerGroups().values() ) {
	    _assign( _v( tg.name() ), 
		_call( tfe, "makeTimerGroup", 
		    _const(tg.name()), _const(tg.description()))) ;
	}
	
	// fill in timer group containment
	// Signature addSig = _s( _boolean(), _t("Controllable")) ;
	for (TimerGroup tg : tf.timerGroups().values() ) {
	    for (Controllable c : tg.contents() ) {
		_expr( 
		    _call( _v(tg.name()), "add", // addSig,
			_v(c.name()))) ;
	    }
	}

	_end() ;
    }

    private static void generateAccessorMethods( TimerFactory tf,
        boolean isImpl ) {

	int modifiers = isImpl ? (PUBLIC|FINAL) : (PUBLIC|ABSTRACT) ;

        List<Timer> timers = new ArrayList<Timer>( tf.timers().values() ) ;
        Collections.sort( timers, COMP );
	for (Timer t : timers) {
	    _method( modifiers, Timer, t.name()) ;
	    if (isImpl) {
		_body() ;
		_return(_field(_this(), t.name())) ;
	    }
	    _end() ;
	}

        List<TimerGroup> timerGroups = new ArrayList<TimerGroup>(
            tf.timerGroups().values() ) ;
        Collections.sort( timerGroups, COMP );
	for (TimerGroup tg : timerGroups) {
	    _method( modifiers, TimerGroup, tg.name()) ;
	    if (isImpl) {
		_body() ;
		_return(_field(_this(), tg.name())) ;
	    }
	    _end() ;
	}

    }

    private static void writeFile( String dirName,
	Type type ) throws IOException {

	File file = ASMUtil.getFile( dirName, type.name(),
	    ".java" ) ;

	PrintStream ps = new PrintStream( file ) ;
	try {
	    _sourceCode( ps, new Properties() ) ;
	} finally {
	    ps.close() ;
	}
    }
}
