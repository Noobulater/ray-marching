package org.sunflow.core.primitive;

import org.sunflow.SunflowAPI;
import org.sunflow.core.Instance;
import org.sunflow.core.IntersectionState;
import org.sunflow.core.LightSample;
import org.sunflow.core.LightSource;
import org.sunflow.core.ParameterList;
import org.sunflow.core.PrimitiveList;
import org.sunflow.core.Ray;
import org.sunflow.core.Shader;
import org.sunflow.core.ShadingState;
import org.sunflow.core.shader.TerrainShader;
import org.sunflow.image.Color;
import org.sunflow.math.BoundingBox;
import org.sunflow.math.Matrix4;
import org.sunflow.math.Noise;
import org.sunflow.math.OrthoNormalBasis;
import org.sunflow.math.Point3;
import org.sunflow.math.Vector3;

public class Terrain implements PrimitiveList, Shader {
	private float eps = 0.01f;

    public Terrain() {
    }
	
	@Override
	public boolean update(ParameterList pl, SunflowAPI api) {
		return true;
	}

	@Override
	public BoundingBox getWorldBounds(Matrix4 o2w) {
        BoundingBox bounds = new BoundingBox(1);
        if (o2w != null)
            bounds = o2w.transform(bounds);
        return bounds;
	}

	@Override
    public float getPrimitiveBound(int primID, int i) {
        return (i & 1) == 0 ? -1 : 1;
    }

	@Override
    public int getNumPrimitives() {
        return 1;
    }
	
	@Override
	public PrimitiveList getBakingPrimitives() {
		return null;
	}
	
	private double roundTo(double x, double size) {
		return Math.floor(x / size) * size;
	}
	
	private double lerp(double x, double y, double t) {
		return x + (y - x) * t;
	}
	
	private double rockHeight(double x, double y) {
		double height = 0.0;
		
		double mountain = Noise.simplexNoise(x, y, 6, 3.0, 0.3, 0.001, 3);
		mountain = Math.pow(mountain, 2) * 16;
		mountain = lerp(mountain, roundTo(mountain, 8.0f), 0.25);
		mountain += Noise.sharpNoise(x, y, 6, 0.1, 0.3, 0.05, 2);
						
		height = mountain;
		
		return height;
	}
	
	private double sandHeight(double x, double y) {
		double height = 0.0;
		
		height = Noise.sharpNoise(x, y, 2, 3.0, 0.4, 0.001, 3);
		height += Noise.simplexNoise(x, y, 5, 0.003, 0.3, 0.05, 3);

		return height;
	}
	
	private double height(double x, double y) {		
//		return rockHeight(x, y);
		return Math.max(rockHeight(x, y), sandHeight(x, y));
	}
	
	float distPlane(float x, float y, float z) {
	    return (float) (z - height(x, y));
	}
	
	int MAX_ITERATIONS = 5000;
	
	float startDelta = 0.5f;
	float stopDelta = 100000.0f;
	
	float computeDist(float x, float y, float z) {
	    float dist = distPlane(x, y, z);
	    
	    return dist;
	}
	
	float getDistToObjects( Vector3 camPos, Vector3 rayDir ) {
		float t = startDelta;
		
		for (int i = 0; i < MAX_ITERATIONS; ++i) {
			float h = computeDist(camPos.x + rayDir.x * t, camPos.y + rayDir.y * t, camPos.z + rayDir.z * t);
			if (h < (0.002 * t) || t > stopDelta)
				break;
			t += 0.5 * h;
			if (i == MAX_ITERATIONS - 1) {
				return 0.0f;
			}
		}
		
		return t;
	}
	
	Vector3 getNormalAtPoint(float px, float py, float epss) {
		float x = (float) (height(px - epss, py) - height(px + epss, py));
		float y = (float) (height(px, py - epss) - height(px, py + epss));
		float z = 2.0f * epss;
		float len = (float) Math.sqrt(x * x + y * y + z * z);
		return new Vector3(x / len, y / len, z / len);
	}
	
	Vector3 getSmoothedNormalAtPoint(float px, float py) {
		Vector3 fine = getNormalAtPoint(px, py, eps);
		Vector3 smooth = getNormalAtPoint(px, py, eps * 200);
		double t = 0.5;
		Vector3 lerped = new Vector3((float)lerp(fine.x, smooth.x, t), (float)lerp(fine.y, smooth.y, t), (float)lerp(fine.z, smooth.z, t));
		return lerped.normalize();
	}
	
    public void intersectPrimitive(Ray r, int primID, IntersectionState state) {
		float t = getDistToObjects(new Vector3(r.ox, r.oy, r.oz), new Vector3(r.dx, r.dy, r.dz));
		if (t < stopDelta * 0.5) {
			state.setIntersection(0, t, 0);
		}
    }
    
    public void scatterPhoton(ShadingState state, Color power) {
        Color kd = Color.WHITE;
        // make sure we are on the right side of the material
        if (Vector3.dot(state.getNormal(), state.getRay().getDirection()) > 0) {
            state.getNormal().negate();
            state.getGeoNormal().negate();
        }
        state.storePhoton(state.getRay().getDirection(), power, kd);
        double avg = kd.getAverage();
        double rnd = state.getRandom(0, 0, 1);
        if (rnd < avg) {
            // photon is scattered
            power.mul(kd).mul(1 / (float) avg);
            OrthoNormalBasis onb = OrthoNormalBasis.makeFromW(state.getNormal());
            double u = 2 * Math.PI * rnd / avg;
            double v = state.getRandom(0, 1, 1);
            float s = (float) Math.sqrt(v);
            float s1 = (float) Math.sqrt(1.0 - v);
            Vector3 w = new Vector3((float) Math.cos(u) * s, (float) Math.sin(u) * s, s1);
            w = onb.transform(w, new Vector3());
            state.traceDiffusePhoton(new Ray(state.getPoint(), w), power);
        }
    }
    
    private Color radiance = Color.WHITE;
    
    public Color getRadiance(ShadingState state) {
        Color kd = Color.WHITE;
        // make sure we are on the right side of the material
        state.faceforward();
        // setup lighting
        state.initLightSamples();
        state.initCausticSamples();
        return state.diffuse(kd);
    }
    
	@Override
	public void prepareShadingState(ShadingState state) {
        state.init();
        
        Ray ray = state.getRay();
	    float px = ray.ox + ray.dx * state.getU();
	    float py = ray.oy + ray.dy * state.getU();
	    float pz = ray.oz + ray.dz * state.getU();
        state.getPoint().set(new Point3(px, py, pz));
        
        Vector3 worldNormal = getSmoothedNormalAtPoint(px, py);
        state.getNormal().set(worldNormal);
        state.getGeoNormal().set(worldNormal);
        state.setBasis(OrthoNormalBasis.makeFromW(worldNormal));
        
        Instance parent = state.getInstance();
        state.setShader(parent.getShader(0));
        state.setModifier(parent.getModifier(0));
	}
}
