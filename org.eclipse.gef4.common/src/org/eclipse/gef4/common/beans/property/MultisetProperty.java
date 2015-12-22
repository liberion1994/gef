/******************************************************************************
 * Copyright (c) 2015 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *     
 *******************************************************************************/
package org.eclipse.gef4.common.beans.property;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.gef4.common.beans.value.WritableMultisetValue;
import org.eclipse.gef4.common.collections.ObservableMultiset;

import com.google.common.collect.Multiset;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ListProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SetProperty;

/**
 * Abstract base class defining contract for a {@link Property} wrapping a
 * {@link ObservableMultiset}.
 * <p>
 * This class provides identical functionality for {@link Multiset} as
 * {@link MapProperty} for {@link Map}, {@link SetProperty} for {@link Set}, or
 * {@link ListProperty} for {@link List}.
 * 
 * @param <E>
 *            The element type of the wrapped {@link ObservableMultiset}.
 * 
 */
public abstract class MultisetProperty<E> extends ReadOnlyMultisetProperty<E>
		implements Property<ObservableMultiset<E>>, WritableMultisetValue<E> {

	@Override
	public void setValue(ObservableMultiset<E> v) {
		set(v);
	}

	@Override
	public void bindBidirectional(Property<ObservableMultiset<E>> other) {
		Bindings.bindBidirectional(this, other);
	}

	@Override
	public void unbindBidirectional(Property<ObservableMultiset<E>> other) {
		Bindings.unbindBidirectional(this, other);
	}
}
