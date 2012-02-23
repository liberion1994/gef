/*******************************************************************************
 * Copyright (c) 2011 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *     Matthias Wienand (itemis AG) - contribution for Bugzilla #355997
 *     
 *******************************************************************************/
package org.eclipse.gef4.geometry.planar;

import org.eclipse.gef4.geometry.Point;
import org.eclipse.gef4.geometry.transform.AffineTransform;
import org.eclipse.gef4.geometry.utils.PointListUtils;
import org.eclipse.gef4.geometry.utils.PrecisionUtils;

/**
 * Represents the geometric shape of a polyline.
 * 
 * Note that while all manipulations (e.g. within shrink, expand) within this
 * class are based on double precision, all comparisons (e.g. within contains,
 * intersects, equals, etc.) are based on a limited precision (with an accuracy
 * defined within {@link PrecisionUtils}) to compensate for rounding effects.
 * 
 * @author anyssen
 */
public class Polyline extends AbstractPointListBasedGeometry implements IPolyCurve {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new {@link Polyline} from a even-numbered sequence of
	 * coordinates. Similar to {@link Polyline#Polyline(Point...)}, only that
	 * coordinates of points rather than {@link Point}s are provided.
	 * 
	 * @param coordinates
	 *            an alternating, even-numbered sequence of x- and
	 *            y-coordinates, representing the points from which the
	 *            {@link Polyline} is to be created .
	 */
	public Polyline(double... coordinates) {
		super(coordinates);
	}

	/**
	 * Constructs a new {@link Polyline} from the given sequence of
	 * {@link Point} s. The {@link Polyline} that is created will be
	 * automatically closed, i.e. it will not only contain a segment between
	 * succeeding points of the sequence but as well back from the last to the
	 * first point.
	 * 
	 * @param points
	 *            a sequence of points, from which the {@link Polyline} is to be
	 *            created.
	 */
	public Polyline(Point... points) {
		super(points);
	}

	/**
	 * Checks whether the point that is represented by its x- and y-coordinates
	 * is contained within this {@link Polyline}.
	 * 
	 * @param x
	 *            the x-coordinate of the point to test
	 * @param y
	 *            the y-coordinate of the point to test
	 * @return <code>true</code> if the point represented by its coordinates if
	 *         contained within this {@link Polyline}, <code>false</code>
	 *         otherwise
	 */
	public boolean contains(double x, double y) {
		return contains(new Point(x, y));
	}

	/**
	 * @see IGeometry#contains(Point)
	 */
	public boolean contains(Point p) {
		for (int i = 0; i + 1 < points.length; i++) {
			Point p1 = points[i];
			Point p2 = points[i + 1];
			if (new Line(p1, p2).contains(p)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @see IGeometry#contains(Rectangle)
	 */
	public boolean contains(Rectangle r) {
		// may contain the rectangle only in case it is degenerated
		return false;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof Polygon) {
			Polyline p = (Polyline) o;
			return equals(p.getPoints());
		}
		return false;
	}

	/**
	 * Checks whether this {@link Polyline} and the one that is indirectly given
	 * via the sequence of points are regarded to be equal. The {@link Polyline}
	 * s will be regarded equal, if they are characterized by the same segments.
	 * 
	 * @param points
	 *            an array of {@link Point} characterizing a {@link Polyline} to
	 *            be checked for equality
	 * @return <code>true</code> if the sequence of points that characterize
	 *         this {@link Polyline} and the {@link Polyline} indirectly given
	 *         via the array of points are regarded to form the same segments.
	 */
	public boolean equals(Point... points) {
		if (points.length != this.points.length) {
			return false;
		}
		return PointListUtils.equals(this.points, points);
	}

	/**
	 * Returns a sequence of {@link Line}s, representing the segments that are
	 * obtained by linking each two successive point of this {@link Polyline}
	 * (including the last and the first one).
	 * 
	 * @return an array of {@link Line}s, representing the segments that make up
	 *         this {@link Polyline}
	 */
	public Line[] getSegments() {
		return PointListUtils.toSegmentsArray(points, false);
	}

	/**
	 * @see IGeometry#getTransformed(AffineTransform)
	 */
	public IGeometry getTransformed(AffineTransform t) {
		return new Polyline(t.getTransformed(points));
	}

	/**
	 * @see IGeometry#intersects(Rectangle)
	 */
	public boolean intersects(Rectangle rect) {
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	/**
	 * @see IGeometry#toPath()
	 */
	public Path toPath() {
		Path path = new Path();
		if (points.length > 0) {
			path.moveTo(points[0].x, points[0].y);
			for (int i = 1; i < points.length; i++) {
				path.lineTo(points[i].x, points[i].y);
			}
		}
		return path;
	}

	@Override
	public String toString() {
		StringBuffer stringBuffer = new StringBuffer("Polyline: ");
		if (points.length > 0) {
			for (int i = 0; i < points.length; i++) {
				stringBuffer.append("(" + points[i].x + ", " + points[i].y
						+ ")");
				if (i < points.length - 1) {
					stringBuffer.append(" -> ");
				}
			}
		} else {
			stringBuffer.append("<no points>");
		}
		return stringBuffer.toString();
	}

	/**
	 * @see org.eclipse.gef4.geometry.planar.IGeometry#getCopy()
	 */
	public Polyline getCopy() {
		return new Polyline(getPoints());
	}

}
