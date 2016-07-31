package org.sunflow.math;

public class Noise {
	static double cx =  0.211324865405187; // (3.0-sqrt(3.0))/6.0
	static double cy =  0.366025403784439; // 0.5*(sqrt(3.0)-1.0)
	static double cz = -0.577350269189626; // -1.0 + 2.0 * C.x
	static double cw =  0.024390243902439; // 1.0 / 41.0

	static private double fract(double x) {
	    return x - Math.floor(x);
	}

	static private double dot2(double x1, double y1, double x2, double y2) {
	    return x1 * x2 + y1 * y2;
	}

	static private double dot3(double x1, double y1, double z1, double x2, double y2, double z2) {
	    return x1 * x2 + y1 * y2 + z1 * z2;
	}

	static private double mod289(double x) {
	    return x - Math.floor(x * (1.0 / 289.0)) * 289.0;
	}

	static private double permute(double x) {
	    return mod289(((x * 34.0) + 1.0) * x);
	}

	static public double noise(double vx, double vy) {
	    // First corner
	    double dotVCYY = dot2(vx, vy, cy, cy);
	    double ix = Math.floor(vx + dotVCYY);
	    double iy = Math.floor(vy + dotVCYY);
	    double dotICXX = dot2(ix, iy, cx, cx);
	    double x0x = vx - ix + dotICXX;
	    double x0y = vy - iy + dotICXX;

	    // Other corners
	    double i1x = 0.0;
	    double i1y = 0.0;
	    // i1.x = step( x0.y, x0.x ) // x0.x > x0.y ? 1.0 : 0.0
	    // i1.y = 1.0 - i1.x
	    if (x0x > x0y) {
	        i1x = 1.0;
	        i1y = 0.0;
	    } else {
	        i1x = 0.0;
	        i1y = 1.0;
	    }
	    // x0 = x0 - 0.0 + 0.0 * C.xx
	    // x1 = x0 - i1 + 1.0 * C.xx
	    // x2 = x0 - 1.0 + 2.0 * C.xx
	    double x12x = x0x + cx;
	    double x12y = x0y + cx;
	    double x12z = x0x + cz;
	    double x12w = x0y + cz;
	    x12x = x12x - i1x;
	    x12y = x12y - i1y;

	    // Permutations
	    ix = mod289(ix); // Avoid truncation effects in permutation
	    iy = mod289(iy);

	    double ppx = permute(0.0 + iy);
	    double ppy = permute(i1y + iy);
	    double ppz = permute(1.0 + iy);
	    double px = permute(0.0 + ppx + ix);
	    double py = permute(i1x + ppy + ix);
	    double pz = permute(1.0 + ppz + ix);

	    double mx = Math.max(0.5 - dot2(x0x, x0y, x0x, x0y), 0.0);
	    double my = Math.max(0.5 - dot2(x12x, x12y, x12x, x12y), 0.0);
	    double mz = Math.max(0.5 - dot2(x12z, x12w, x12z, x12w), 0.0);
	    mx = mx * mx;
	    mx = mx * mx;
	    my = my * my;
	    my = my * my;
	    mz = mz * mz;
	    mz = mz * mz;

	    // Gradients: 41 points uniformly over a line, mapped onto a diamond.
	    // The ring size 17*17 = 289 is close to a multiple of 41 (41*7 = 287)
	    double xx = 2.0 * fract(px * cw) - 1.0;
	    double xy = 2.0 * fract(py * cw) - 1.0;
	    double xz =  2.0 * fract(pz * cw) - 1.0;

	    double hx = Math.abs(xx) - 0.5;
	    double hy = Math.abs(xy) - 0.5;
	    double hz = Math.abs(xz) - 0.5;

	    double a0x = xx - Math.floor(xx + 0.5);
	    double a0y = xy - Math.floor(xy + 0.5);
	    double a0z = xz - Math.floor(xz + 0.5);

	    // Normalise gradients implicitly by scaling m
	    // Approximation of: m *= inversesqrt( a0*a0 + h*h )
	    mx = mx * (1.79284291400159 - 0.85373472095314 * (a0x * a0x + hx * hx));
	    my = my * (1.79284291400159 - 0.85373472095314 * (a0y * a0y + hy * hy));
	    mz = mz * (1.79284291400159 - 0.85373472095314 * (a0z * a0z + hz * hz));

	    // Compute final noise value at P
	    double gx = a0x * x0x + hx * x0y;
	    double gy = a0y * x12x + hy * x12y;
	    double gz = a0z * x12z + hz * x12w;

	    return 130.0 * dot3(mx, my, mz, gx, gy, gz);
	}
	
	static public double simplexNoise(double x, double y, int octaves, double amp, double gain, double freq, double lac) {
		double sum = 0.0;
		for (int i = 0; i < octaves; ++i) {
			sum += noise(x * freq, y * freq) * amp;
			amp *= gain;
			freq *= lac;
		}
		return sum;
	}
	
	static public double sharpNoise(double x, double y, int octaves, double amp, double gain, double freq, double lac) {
		double sum = 0.0;
		for (int i = 0; i < octaves; ++i) {
			sum += (1.0 - Math.abs(noise(x * freq, y * freq) * 2.0 - 1.0)) * amp;
			amp *= gain;
			freq *= lac;
		}
		return sum;
		
	}
}
