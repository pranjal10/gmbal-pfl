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

package org.glassfish.pfl.dynamic.codegen.spi;

/** Interceptor interface used for byte code modification.
 * A user supplies an implementation of this interface.
 * The calls are invoked as follows:
 * <ol>
 * <li>handleClass is called for the class.
 * <li>handleMethod is called for each method defined in the class.
 * <li>handleFieldReference is called for each field reference in
 * a class in the order in which they occur.  All field references
 * in a method are made available for modification before 
 * handleMethod is called for the next method.
 * </ol>
 */
public interface Interceptor extends Comparable<Interceptor> {
    /** Return the name of the interceptor.
     */
    String name() ;

    /** Invoked when the GenericClass constructor is called with
     * classdata.  All Wrapper methods that are available 
     * between _class() and _end() may be used to add to
     * cls.  This includes adding new methods, fields, constructors,
     * and extending the class initializer.  Any changes made to
     * the ModifiableClass argument are included in the resulting
     * GenericClass instance.
     */
    void handleClass( ModifiableClass cls ) ;
	    
    /** Invoked after handleClass for each method defined in the
     * class passed into the GenericClass constructor called
     * with the classdata.  The ModifiableMethod API may be
     * used to change the method, including adding code
     * before and/or after the existing method body using
     * the usual Wrapper calls for use in a method body.
     */
    void handleMethod( ModifiableMethod method ) ;
	    
    /** Called when a reference to a field is encountered while
     * visiting the body of the method for which handleMethod
     * was most recently called.
     */
    void handleFieldReference( ModifiableFieldReference ref ) ;
}
