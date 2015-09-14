/*******************************************************************************
 * Copyright (c) 2014 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.mvc.policies;

import org.eclipse.gef4.mvc.operations.AddContentChildOperation;
import org.eclipse.gef4.mvc.operations.AttachToContentAnchorageOperation;
import org.eclipse.gef4.mvc.operations.DetachFromContentAnchorageOperation;
import org.eclipse.gef4.mvc.operations.ForwardUndoCompositeOperation;
import org.eclipse.gef4.mvc.operations.ITransactional;
import org.eclipse.gef4.mvc.operations.ITransactionalOperation;
import org.eclipse.gef4.mvc.operations.RemoveContentChildOperation;
import org.eclipse.gef4.mvc.operations.SynchronizeContentAnchoragesOperation;
import org.eclipse.gef4.mvc.operations.SynchronizeContentChildrenOperation;
import org.eclipse.gef4.mvc.parts.IContentPart;
import org.eclipse.gef4.mvc.parts.IVisualPart;

/**
 * A (transaction) policy to handle content changes, i.e. adding/removing of
 * content children, as well as attaching/detaching to/from content anchorages.
 *
 * @author anyssen
 *
 * @param <VR>
 *            The visual root node of the UI toolkit used, e.g.
 *            javafx.scene.Node in case of JavaFX.
 */
public class ContentPolicy<VR> extends AbstractPolicy<VR>
		implements ITransactional {

	/**
	 * Stores the <i>initialized</i> flag for this policy, i.e.
	 * <code>true</code> after {@link #init()} was called, and
	 * <code>false</code> after {@link #commit()} was called, respectively.
	 */
	protected boolean initialized;

	private ForwardUndoCompositeOperation commitOperation;

	/**
	 * Creates and records operations to add the given <i>contentChild</i> to
	 * the {@link #getHost() host} of this {@link ContentPolicy} at the
	 * specified <i>index</i>.
	 *
	 * @param contentChild
	 *            The content {@link Object} that is to be added to the
	 *            {@link #getHost() host} of this {@link ContentPolicy}.
	 * @param index
	 *            The index of the new content child.
	 */
	public void addContentChild(Object contentChild, int index) {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		ForwardUndoCompositeOperation addOperation = new ForwardUndoCompositeOperation(
				"Add Content Child");
		addOperation.add(new AddContentChildOperation<VR>(getHost(),
				contentChild, index));
		addOperation.add(new SynchronizeContentChildrenOperation<VR>(
				"Synchronize Children", getHost()));
		commitOperation.add(addOperation);
	}

	/**
	 * Creates and records operations to attach the {@link #getHost() host} of
	 * this {@link ContentPolicy} to the specified <i>contentAnchorage</i> under
	 * the specified <i>role</i>.
	 *
	 * @param contentAnchorage
	 *            The content {@link Object} to which the {@link #getHost()
	 *            host} of this {@link ContentPolicy} is to be attached.
	 * @param role
	 *            The role for the attachment.
	 *
	 */
	public void attachToContentAnchorage(Object contentAnchorage, String role) {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		ForwardUndoCompositeOperation attachOperation = new ForwardUndoCompositeOperation(
				"Attach To Content Anchorage");
		attachOperation.add(new AttachToContentAnchorageOperation<VR>(getHost(),
				contentAnchorage, role));
		attachOperation.add(new SynchronizeContentAnchoragesOperation<VR>(
				"Synchronize Anchorages", getHost()));
		commitOperation.add(attachOperation);
	}

	@Override
	public ITransactionalOperation commit() {
		// after commit, we need to be re-initialized
		initialized = false;
		if (commitOperation != null) {
			ITransactionalOperation commit = commitOperation.unwrap(true);
			commitOperation = null;
			return commit;
		}
		return null;
	}

	/**
	 * Creates and records operations to delete the {@link #getHost() host} of
	 * this {@link ContentPolicy} from the content model, i.e. detaches all
	 * content anchoreds, detaches from all content anchorages, and removes from
	 * its parent's content children.
	 */
	public void deleteContent() {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		// unestablish anchor relations
		detachAllContentAnchoreds();
		detachFromAllContentAnchorages();
		// remove content from parent
		removeFromParent();
	}

	/**
	 * Creates and records operations to detach all content anchoreds from the
	 * {@link #getHost() host} of this {@link ContentPolicy}.
	 */
	public void detachAllContentAnchoreds() {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		ForwardUndoCompositeOperation detachOps = new ForwardUndoCompositeOperation(
				"Detach All Anchoreds");
		for (IVisualPart<VR, ? extends VR> anchored : getHost()
				.getAnchoreds()) {
			if (anchored instanceof IContentPart) {
				ContentPolicy<VR> policy = anchored
						.<ContentPolicy<VR>> getAdapter(ContentPolicy.class);
				if (policy != null) {
					policy.init();
					for (String role : anchored.getAnchorages()
							.get(getHost())) {
						policy.detachFromContentAnchorage(
								getHost().getContent(), role);
					}
					ITransactionalOperation detachOperation = policy.commit();
					if (detachOperation != null) {
						detachOps.add(detachOperation);
					}
				}
			}
		}
		ITransactionalOperation detachOperation = detachOps.unwrap(true);
		if (detachOperation != null) {
			commitOperation.add(detachOperation);
		}
	}

	/**
	 * Creates and records operations to detach the {@link #getHost() host} of
	 * this {@link ContentPolicy} from all content anchorages.
	 */
	public void detachFromAllContentAnchorages() {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		for (IVisualPart<VR, ? extends VR> anchorage : getHost().getAnchorages()
				.keySet()) {
			if (anchorage instanceof IContentPart) {
				for (String role : getHost().getAnchorages().get(anchorage)) {
					detachFromContentAnchorage(
							((IContentPart<VR, ? extends VR>) anchorage)
									.getContent(),
							role);
				}
			}
		}
	}

	/**
	 * Creates and records operations to detach the {@link #getHost() host} of
	 * this {@link ContentPolicy} from the specified <i>contentAnchorage</i>
	 * under the specified <i>role</i>.
	 *
	 * @param contentAnchorage
	 *            The content {@link Object} from which the {@link #getHost()}
	 *            of this {@link ContentPolicy} is detached.
	 * @param role
	 *            The role under which the anchorage is detached.
	 */
	public void detachFromContentAnchorage(Object contentAnchorage,
			String role) {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		// assemble content operations in forward-undo-operations, so that
		// synchronization is always performed after changing the content
		// model (in execute() and undo())
		ForwardUndoCompositeOperation detachOperation = new ForwardUndoCompositeOperation(
				"Detach From Content Anchorage");
		detachOperation.add(new DetachFromContentAnchorageOperation<VR>(
				getHost(), contentAnchorage, role));
		detachOperation.add(new SynchronizeContentAnchoragesOperation<VR>(
				"Synchronize Anchorages", getHost()));
		commitOperation.add(detachOperation);
	}

	@Override
	public IContentPart<VR, ? extends VR> getHost() {
		return (IContentPart<VR, ? extends VR>) super.getHost();
	}

	@Override
	public void init() {
		commitOperation = new ForwardUndoCompositeOperation("Content Change");
		initialized = true;
	}

	/**
	 * Creates and records operations to remove the given <i>contentChild</i>
	 * from the content children of the {@link #getHost() host} of this
	 * {@link ContentPolicy}.
	 *
	 * @param contentChild
	 *            The content {@link Object} that is removed from content
	 *            children of the {@link #getHost() host} of this
	 *            {@link ContentPolicy}.
	 */
	public void removeContentChild(Object contentChild) {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		ForwardUndoCompositeOperation removeOperation = new ForwardUndoCompositeOperation(
				"Remove Content Child");
		removeOperation.add(
				new RemoveContentChildOperation<VR>(getHost(), contentChild));
		removeOperation.add(new SynchronizeContentChildrenOperation<VR>(
				"Synchronize Children", getHost()));
		commitOperation.add(removeOperation);
	}

	/**
	 * Creates and records operations to remove the content of this
	 * {@link ContentPolicy}'s {@link #getHost() host} from its parent.
	 */
	public void removeFromParent() {
		// ensure we have been properly initialized
		if (!initialized) {
			throw new IllegalStateException("Not yet initialized!");
		}
		ForwardUndoCompositeOperation deleteOps = new ForwardUndoCompositeOperation(
				"Delete Content");
		if (getHost().getParent() instanceof IContentPart) {
			ContentPolicy<VR> policy = getHost().getParent()
					.<ContentPolicy<VR>> getAdapter(ContentPolicy.class);
			if (policy != null) {
				policy.init();
				policy.removeContentChild(getHost().getContent());
				ITransactionalOperation removeOperation = policy.commit();
				if (removeOperation != null) {
					deleteOps.add(removeOperation);
				}
			}
		}
		ITransactionalOperation deleteOperation = deleteOps.unwrap(true);
		if (deleteOperation != null) {
			commitOperation.add(deleteOperation);
		}
	}

	@Override
	public void setAdaptable(IVisualPart<VR, ? extends VR> adaptable) {
		if (!(adaptable instanceof IContentPart)) {
			throw new IllegalStateException(
					"A ContentPolicy may only be attached to an IContentPart.");
		}
		super.setAdaptable(adaptable);
	}

}
