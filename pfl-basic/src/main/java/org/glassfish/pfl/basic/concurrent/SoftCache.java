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

package org.glassfish.pfl.basic.concurrent ;

import java.lang.ref.SoftReference;
import java.lang.ref.ReferenceQueue;

import java.util.Iterator;
import java.util.Map;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Set;
import java.util.AbstractSet;
import java.util.NoSuchElementException;


/**
 * A memory-sensitive implementation of the <code>Map</code> interface.
 *
 * <p> A <code>SoftCache</code> object uses {@link java.lang.ref.SoftReference
 * soft references} to implement a memory-sensitive hash map.  If the garbage
 * collector determines at a certain point in time that a value object in a
 * <code>SoftCache</code> entry is no longer strongly reachable, then it may
 * remove that entry in order to release the memory occupied by the value
 * object.  All <code>SoftCache</code> objects are guaranteed to be completely
 * cleared before the virtual machine will throw an
 * <code>OutOfMemoryError</code>.  Because of this automatic clearing feature,
 * the behavior of this class is somewhat different from that of other
 * <code>Map</code> implementations.
 *
 * <p> Both null values and the null key are supported.  This class has the
 * same performance characteristics as the <code>HashMap</code> class, and has
 * the same efficiency parameters of <em>initial capacity</em> and <em>load
 * factor</em>.
 *
 * <p> Like most collection classes, this class is not synchronized.  A
 * synchronized <code>SoftCache</code> may be constructed using the
 * <code>Collections.synchronizedMap</code> method.
 *
 * <p> In typical usage this class will be subclassed and the <code>fill</code>
 * method will be overridden.  When the <code>get</code> method is invoked on a
 * key for which there is no mapping in the cache, it will in turn invoke the
 * <code>fill</code> method on that key in an attempt to construct a
 * corresponding value.  If the <code>fill</code> method returns such a value
 * then the cache will be updated and the new value will be returned.  Thus,
 * for example, a simple URL-content cache can be constructed as follows:
 *
 * <pre>
 *     public class URLCache extends SoftCache {
 *         protected Object fill(Object key) {
 *             return ((URL)key).getContent();
 *         }
 *     }
 * </pre>
 *
 * <p> The behavior of the <code>SoftCache</code> class depends in part upon
 * the actions of the garbage collector, so several familiar (though not
 * required) <code>Map</code> invariants do not hold for this class.  <p>
 * Because entries are removed from a <code>SoftCache</code> in response to
 * dynamic advice from the garbage collector, a <code>SoftCache</code> may
 * behave as though an unknown thread is silently removing entries.  In
 * particular, even if you synchronize on a <code>SoftCache</code> instance and
 * invoke none of its mutator methods, it is possible for the <code>size</code>
 * method to return smaller values over time, for the <code>isEmpty</code>
 * method to return <code>false</code> and then <code>true</code>, for the
 * <code>containsKey</code> method to return <code>true</code> and later
 * <code>false</code> for a given key, for the <code>get</code> method to
 * return a value for a given key but later return <code>null</code>, for the
 * <code>put</code> method to return <code>null</code> and the
 * <code>remove</code> method to return <code>false</code> for a key that
 * previously appeared to be in the map, and for successive examinations of the
 * key set, the value set, and the entry set to yield successively smaller
 * numbers of elements.
 * <p>
 * I copied this from JDK 1.4.2 to avoid introducing another sun.misc
 * dependency in the ORB. (Ken Cavanaugh)
 *
 * @version	1.6, 03/01/23
 * @author	Mark Reinhold
 * @since	JDK1.2
 * @see		java.util.HashMap
 * @see		java.lang.ref.SoftReference
 */


public class SoftCache<K,V> extends AbstractMap<K,V> implements Map<K,V> {

    /* The basic idea of this implementation is to maintain an internal HashMap
       that maps keys to soft references whose referents are the keys' values;
       the various accessor methods dereference these soft references before
       returning values.  Because we don't have access to the innards of the
       HashMap, each soft reference must contain the key that maps to it so
       that the processQueue method can remove keys whose values have been
       discarded.  Thus the HashMap actually maps keys to instances of the
       ValueCell class, which is a simple extension of the SoftReference class.
     */


    static private class ValueCell<K,V> extends SoftReference<V> {
	static private int dropped = 0;
	private boolean keyIsValid ;
	private K key;

	private ValueCell(K key, V value, ReferenceQueue<V> queue) {
	    super(value, queue);
	    this.key = key;
	    keyIsValid = true ;
	}

        @SuppressWarnings("unchecked")
	private static <K,V> ValueCell<K,V> create(
	    K key, V value, ReferenceQueue<V> queue)
	{
	    if (value == null) {
                return null;
            }
	    return new ValueCell(key, value, queue);
	}

	private static <K,V> V strip(ValueCell<K,V> val, boolean drop) {
	    if (val == null) {
                return null;
            }
	    V o = val.get();
	    if (drop) {
                val.drop();
            }
	    return o;
	}

	private boolean isValid() {
	    return keyIsValid; 
	}

	private void drop() {
	    super.clear();
	    keyIsValid = false ;
	    dropped++;
	}
    }

    private static final int PROCESS_QUEUE_INTERVAL = 10 ;

    private int processQueueCount = PROCESS_QUEUE_INTERVAL ;

    /* Hash table mapping keys to ValueCells */
    private Map<K,ValueCell<K,V>> hash;

    /* Reference queue for cleared ValueCells */
    private ReferenceQueue<V> queue = 
	new ReferenceQueue<V>();


    /* Process any ValueCells that have been cleared and enqueued by the
       garbage collector.  This method should be invoked once by each public
       mutator in this class.  We don't invoke this method in public accessors
       because that can lead to surprising ConcurrentModificationExceptions.
     */
    @SuppressWarnings("unchecked")
    private void processQueue() {
	if (--processQueueCount == 0) {
	    processQueueCount = PROCESS_QUEUE_INTERVAL ;

	    ValueCell<K,V> vc;
	    while ((vc = (ValueCell<K,V>)queue.poll()) != null) {
		if (vc.isValid()) {
                    hash.remove(vc.key);
                } else {
                    ValueCell.dropped--;
                }
	    }
	}
    }

    /* -- Constructors -- */

    /**
     * Construct a new, empty <code>SoftCache</code> with the given
     * initial capacity and the given load factor.
     *
     * @param  initialCapacity  The initial capacity of the cache
     *
     * @param  loadFactor       A number between 0.0 and 1.0
     *
     * @throws IllegalArgumentException  If the initial capacity is less than
     *                                   or equal to zero, or if the load
     *                                   factor is less than zero
     */
    public SoftCache(int initialCapacity, float loadFactor) {
	hash = new HashMap<K,ValueCell<K,V>>(initialCapacity, loadFactor);
    }

    /**
     * Construct a new, empty <code>SoftCache</code> with the given
     * initial capacity and the default load factor.
     *
     * @param  initialCapacity  The initial capacity of the cache
     *
     * @throws IllegalArgumentException  If the initial capacity is less than
     *                                   or equal to zero
     */
    public SoftCache(int initialCapacity) {
	hash = new HashMap<K,ValueCell<K,V>>(initialCapacity);
    }

    /**
     * Construct a new, empty <code>SoftCache</code> with the default
     * capacity and the default load factor.
     */
    public SoftCache() {
	hash = new HashMap<K,ValueCell<K,V>>();
    }

    /* -- Simple queries -- */

    /**
     * Return the number of key-value mappings in this cache.  The time
     * required by this operation is linear in the size of the map.
     */
    @Override
    public int size() {
	return entrySet().size();
    }

    /**
     * Return <code>true</code> if this cache contains no key-value mappings.
     */
    @Override
    public boolean isEmpty() {
	return entrySet().isEmpty();
    }

    /**
     * Return <code>true</code> if this cache contains a mapping for the
     * specified key.  If there is no mapping for the key, this method will not
     * attempt to construct one by invoking the <code>fill</code> method.
     *
     * @param   key   The key whose presence in the cache is to be tested
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
	return ValueCell.strip(hash.get((K)key), false) != null;
    }

    /* -- Lookup and modification operations -- */

    /**
     * Create a value object for the given <code>key</code>.  This method is
     * invoked by the <code>get</code> method when there is no entry for
     * <code>key</code>.  If this method returns a non-<code>null</code> value,
     * then the cache will be updated to map <code>key</code> to that value,
     * and that value will be returned by the <code>get</code> method.
     *
     * <p> The default implementation of this method simply returns
     * <code>null</code> for every <code>key</code> value.  A subclass may
     * override this method to provide more useful behavior.
     *
     * @param  key  The key for which a value is to be computed
     * 
     * @return      A value for <code>key</code>, or <code>null</code> if one
     *              could not be computed
     * @see #get
     */
    protected V fill(Object key) {
	return null;
    }

    /**
     * Return the value to which this cache maps the specified
     * <code>key</code>.  If the cache does not presently contain a value for
     * this key, then invoke the <code>fill</code> method in an attempt to
     * compute such a value.  If that method returns a non-<code>null</code>
     * value, then update the cache and return the new value.  Otherwise,
     * return <code>null</code>.
     *
     * <p> Note that because this method may update the cache, it is considered
     * a mutator and may cause <code>ConcurrentModificationException</code>s to
     * be thrown if invoked while an iterator is in use.
     *
     * @param  key  The key whose associated value, if any, is to be returned
     *
     * @see #fill
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
	processQueue();
	ValueCell<K,V> vc = hash.get(key);
	if (vc == null) {
	    V v = fill(key);
	    if (v != null) {
		hash.put((K)key, ValueCell.create((K)key, v, queue));
		return v;
	    }
	}
	return ValueCell.strip(vc, false);
    }

    /**
     * Update this cache so that the given <code>key</code> maps to the given
     * <code>value</code>.  If the cache previously contained a mapping for
     * <code>key</code> then that mapping is replaced and the old value is
     * returned.
     *
     * @param  key    The key that is to be mapped to the given
     *                <code>value</code> 
     * @param  value  The value to which the given <code>key</code> is to be
     *                mapped
     *
     * @return  The previous value to which this key was mapped, or
     *          <code>null</code> if if there was no mapping for the key
     */
    @Override
    public V put(K key, V value) {
	processQueue();
	ValueCell<K,V> vc = ValueCell.create(key, value, queue);
	return ValueCell.strip(hash.put(key, vc), true);
    }

    /**
     * Remove the mapping for the given <code>key</code> from this cache, if
     * present.
     *
     * @param  key  The key whose mapping is to be removed
     *
     * @return  The value to which this key was mapped, or <code>null</code> if
     *          there was no mapping for the key
     */
    @Override
    public V remove(Object key) {
	processQueue();
	return ValueCell.strip(hash.remove(key), true);
    }

    /**
     * Remove all mappings from this cache.
     */
    @Override
    public void clear() {
	processQueue();
	hash.clear();
    }

    /* -- Views -- */

    private static boolean valEquals(Object o1, Object o2) {
	return (o1 == null) ? (o2 == null) : o1.equals(o2);
    }


    /* Internal class for entries.
       Because it uses SoftCache.this.queue, this class cannot be static.
     */
    private class Entry implements Map.Entry<K,V> {
	private Map.Entry<K,ValueCell<K,V>> ent;
	private V value;	/* Strong reference to value, to prevent the GC
				   from flushing the value while this Entry
				   exists */

	Entry(Map.Entry<K,ValueCell<K,V>> ent, V value) {
	    this.ent = ent;
	    this.value = value;
	}

        @Override
	public K getKey() {
	    return ent.getKey();
	}

        @Override
	public V getValue() {
	    return value;
	}

        @Override
	public V setValue(V value) {
	    ent.setValue(ValueCell.create(ent.getKey(), value, queue));
	    return value ;
	}

        @Override
	public boolean equals(Object o) {
	    if (! (o instanceof Map.Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked")
	    Map.Entry<K,V> e = (Map.Entry<K,V>)o;
	    return (valEquals(ent.getKey(), e.getKey())
		    && valEquals(value, e.getValue()));
	}

        @Override
	public int hashCode() {
	    Object k;
	    return ((((k = getKey()) == null) ? 0 : k.hashCode())
		    ^ ((value == null) ? 0 : value.hashCode()));
	}
    }


    /* Internal class for entry sets */
    private class EntrySet extends 
	AbstractSet<Map.Entry<K,V>> {

	Set<Map.Entry<K,ValueCell<K,V>>> hashEntries = hash.entrySet();

        @Override
	public Iterator<Map.Entry<K,V>> iterator() {

	    return new Iterator<Map.Entry<K,V>>() {
		Iterator<Map.Entry<K,ValueCell<K,V>>> hashIterator = 
		    hashEntries.iterator();
		Entry next = null;

                @Override
		public boolean hasNext() {
		    while (hashIterator.hasNext()) {
			Map.Entry<K,ValueCell<K,V>> ent = hashIterator.next();
			ValueCell<K,V> vc = ent.getValue();
			V v = null;
			if ((vc != null) && ((v = vc.get()) == null)) {
			    /* Value has been flushed by GC */
			    continue;
			}
			next = new Entry(ent, v);
			return true;
		    }
		    return false;
		}

                @Override
		public Map.Entry<K,V> next() {
		    if ((next == null) && !hasNext()) {
                        throw new NoSuchElementException();
                    }
		    Map.Entry<K,V> e = next;
		    next = null;
		    return e;
		}

                @Override
		public void remove() {
		    hashIterator.remove();
		}
	    };
	}

        @Override
	public boolean isEmpty() {
	    return !(iterator().hasNext());
	}

        @Override
	public int size() {
	    int j = 0;
	    for (Iterator i = iterator(); i.hasNext(); i.next()) {
                j++;
            }
	    return j;
	}

	public boolean remove(Map.Entry<K,V> o) {
	    processQueue();
	    if (Entry.class.isInstance(o)) {
                return hashEntries.remove(((Entry) o).ent);
            } else {
                return false;
            }
	}
    }

    private Set<Map.Entry<K,V>> entrySet = null;

    /**
     * Return a <code>Set</code> view of the mappings in this cache.
     */
    @Override
    public Set<Map.Entry<K,V>> entrySet() {
	if (entrySet == null) {
            entrySet = new EntrySet();
        }

        return entrySet;
    }
}
