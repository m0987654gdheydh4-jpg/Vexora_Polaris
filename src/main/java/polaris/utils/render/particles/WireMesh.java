package polaris.utils.render.particles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WireMesh {
    private final float[] edges;

    private WireMesh(float[] edges) {
        this.edges = edges == null ? new float[0] : edges;
    }

    public float[] edges() {
        return edges;
    }

    public boolean isEmpty() {
        return edges.length < 6;
    }

    public static WireMesh loadClasspath(String absoluteResourcePath) {
        return loadClasspath(absoluteResourcePath, true, 0f);
    }

    public static WireMesh loadClasspath(String absoluteResourcePath, boolean normalize, float yOffset) {
        InputStream in = WireMesh.class.getResourceAsStream(absoluteResourcePath);
        if (in == null) {
            return new WireMesh(null);
        }
        String lower = absoluteResourcePath.toLowerCase();
        try {
            if (lower.endsWith(".obj")) {
                return loadObj(in, normalize, yOffset);
            }
            if (lower.endsWith(".gltf")) {
                return loadGltf(in);
            }
        } catch (Exception ignored) {
            return new WireMesh(null);
        }
        return new WireMesh(null);
    }

    public static WireMesh proceduralCrescent() {
        float f = 0.5F;
        float f1 = 0.42F;
        float f2 = 0.3F;
        float f3 = 0.13F;
        float f4 = 0.022F;
        float f5 = (f * f - f1 * f1 + f2 * f2) / (2.0F * f2);
        float f6 = (float) Math.sqrt(Math.max(0.0F, f * f - f5 * f5));
        float f7 = (float) Math.atan2(f6, f5);
        float f8 = (float) Math.atan2(f6, f5 - f2);
        int b0 = 18;
        int b1 = 3;
        float f9 = (f - f5) * 0.5F;
        float f10 = f7;
        float f11 = (float) (Math.PI * 2) - 2.0F * f7;
        float f12 = f11 / (float) b0;
        float f13 = f8;
        float f14 = (float) (Math.PI * 2) - 2.0F * f8;
        float f15 = f14 / (float) b0;
        float[] ax = new float[b0 + 1];
        float[] ay = new float[b0 + 1];
        float[] az = new float[b0 + 1];
        for (int i = 0; i <= b0; i++) {
            float f16 = f10 + f12 * i;
            ax[i] = (float) Math.cos(f16) * f + f9;
            ay[i] = (float) Math.sin(f16) * f;
            float t = (f16 - f10) / f11;
            az[i] = f4 + (f3 - f4) * (float) Math.sin(t * Math.PI);
        }
        float[] bx = new float[b0 + 1];
        float[] by = new float[b0 + 1];
        float[] bz = new float[b0 + 1];
        for (int j = 0; j <= b0; j++) {
            float f18 = f13 + f15 * j;
            bx[j] = (float) Math.cos(f18) * f1 + f2 + f9;
            by[j] = (float) Math.sin(f18) * f1;
            float t = (f18 - f13) / f14;
            bz[j] = f4 + (f3 - f4) * (float) Math.sin(t * Math.PI);
        }
        List<Float> out = new ArrayList<>(b0 * 28 + 64);
        for (int k = 0; k < b0; k++) {
            edge(out, ax[k], ay[k], az[k], ax[k + 1], ay[k + 1], az[k + 1]);
            edge(out, ax[k], ay[k], -az[k], ax[k + 1], ay[k + 1], -az[k + 1]);
        }
        for (int l = 0; l < b0; l++) {
            edge(out, bx[l], by[l], bz[l], bx[l + 1], by[l + 1], bz[l + 1]);
            edge(out, bx[l], by[l], -bz[l], bx[l + 1], by[l + 1], -bz[l + 1]);
        }
        for (int b2 = 0; b2 <= b0; b2 += b1) {
            edge(out, ax[b2], ay[b2], az[b2], ax[b2], ay[b2], -az[b2]);
        }
        for (int b3 = b1; b3 < b0; b3 += b1) {
            edge(out, bx[b3], by[b3], bz[b3], bx[b3], by[b3], -bz[b3]);
        }
        return fromList(out);
    }

    public static WireMesh unitCube() {
        List<Float> out = new ArrayList<>(72);
        float h = 0.5f;
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < 4; i++) {
                float a = (i & 1) == 0 ? -h : h;
                float b = (i & 2) == 0 ? -h : h;
                switch (axis) {
                    case 0 -> edge(out, -h, a, b, h, a, b);
                    case 1 -> edge(out, a, -h, b, a, h, b);
                    default -> edge(out, a, b, -h, a, b, h);
                }
            }
        }
        return fromList(out);
    }

    public static WireMesh unitPyramid() {
        List<Float> out = new ArrayList<>(48);
        float h = 0.5f;
        float[][] base = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
        for (int i = 0; i < 4; i++) {
            float[] p = base[i];
            float[] q = base[(i + 1) % 4];
            edge(out, p[0], -h, p[1], q[0], -h, q[1]);
            edge(out, p[0], -h, p[1], 0.0f, h, 0.0f);
        }
        return fromList(out);
    }

    public static WireMesh proceduralOctahedron() {
        List<Float> out = new ArrayList<>(72);
        float r = 0.42f;
        float t = 0.56f;
        float[][] ring = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
        for (int i = 0; i < 4; i++) {
            float[] p = ring[i];
            float[] q = ring[(i + 1) % 4];
            edge(out, p[0], 0, p[1], q[0], 0, q[1]);
            edge(out, p[0], 0, p[1], 0, t, 0);
            edge(out, p[0], 0, p[1], 0, -t, 0);
        }
        return fromList(out);
    }

    public static WireMesh proceduralTetrahedron() {
        List<Float> out = new ArrayList<>(36);
        float s = 0.5f;
        float[][] v = {
                {s, s, s},
                {s, -s, -s},
                {-s, s, -s},
                {-s, -s, s}
        };
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                edge(out, v[i][0], v[i][1], v[i][2], v[j][0], v[j][1], v[j][2]);
            }
        }
        return fromList(out);
    }

    public static WireMesh proceduralIcosahedron() {
        float phi = (1.0f + (float) Math.sqrt(5.0)) / 2.0f;
        float scale = 0.5f / (float) Math.sqrt(1.0f + phi * phi);
        float a = scale;
        float b = phi * scale;
        float[][] v = {
                {0, a, b}, {0, a, -b}, {0, -a, b}, {0, -a, -b},
                {a, b, 0}, {a, -b, 0}, {-a, b, 0}, {-a, -b, 0},
                {b, 0, a}, {-b, 0, a}, {b, 0, -a}, {-b, 0, -a}
        };
        float target = 2.0f * a;
        float tolerance = target * 0.05f;
        List<Float> out = new ArrayList<>(30 * 6);
        for (int i = 0; i < v.length; i++) {
            for (int j = i + 1; j < v.length; j++) {
                float dx = v[i][0] - v[j][0];
                float dy = v[i][1] - v[j][1];
                float dz = v[i][2] - v[j][2];
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (Math.abs(d - target) <= tolerance) {
                    edge(out, v[i][0], v[i][1], v[i][2], v[j][0], v[j][1], v[j][2]);
                }
            }
        }
        return fromList(out);
    }

    public static WireMesh proceduralTorus(int major, int minor) {
        int majorSteps = Math.max(6, major);
        int minorSteps = Math.max(4, minor);
        float ringRadius = 0.38f;
        float tubeRadius = 0.13f;
        float[][][] p = new float[majorSteps][minorSteps][3];
        for (int i = 0; i < majorSteps; i++) {
            double u = i * Math.PI * 2.0 / majorSteps;
            double cu = Math.cos(u);
            double su = Math.sin(u);
            for (int j = 0; j < minorSteps; j++) {
                double vAngle = j * Math.PI * 2.0 / minorSteps;
                double r = ringRadius + tubeRadius * Math.cos(vAngle);
                p[i][j][0] = (float) (cu * r);
                p[i][j][1] = (float) (tubeRadius * Math.sin(vAngle));
                p[i][j][2] = (float) (su * r);
            }
        }
        List<Float> out = new ArrayList<>(majorSteps * minorSteps * 12);
        for (int i = 0; i < majorSteps; i++) {
            int ni = (i + 1) % majorSteps;
            for (int j = 0; j < minorSteps; j++) {
                int nj = (j + 1) % minorSteps;
                edge(out, p[i][j][0], p[i][j][1], p[i][j][2], p[ni][j][0], p[ni][j][1], p[ni][j][2]);
                edge(out, p[i][j][0], p[i][j][1], p[i][j][2], p[i][nj][0], p[i][nj][1], p[i][nj][2]);
            }
        }
        return fromList(out);
    }

    public static WireMesh proceduralHelix() {
        int steps = 36;
        float radius = 0.26f;
        float height = 1.0f;
        float turns = 2.0f;
        List<Float> out = new ArrayList<>(steps * 24);
        float[] ax = new float[steps + 1];
        float[] ay = new float[steps + 1];
        float[] az = new float[steps + 1];
        float[] bx = new float[steps + 1];
        float[] by = new float[steps + 1];
        float[] bz = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            double angle = t * turns * Math.PI * 2.0;
            float y = (t - 0.5f) * height;
            ax[i] = (float) Math.cos(angle) * radius;
            ay[i] = y;
            az[i] = (float) Math.sin(angle) * radius;
            bx[i] = -ax[i];
            by[i] = y;
            bz[i] = -az[i];
        }
        for (int i = 0; i < steps; i++) {
            edge(out, ax[i], ay[i], az[i], ax[i + 1], ay[i + 1], az[i + 1]);
            edge(out, bx[i], by[i], bz[i], bx[i + 1], by[i + 1], bz[i + 1]);
        }
        for (int i = 0; i <= steps; i += 4) {
            edge(out, ax[i], ay[i], az[i], bx[i], by[i], bz[i]);
        }
        return fromList(out);
    }

    public static WireMesh proceduralGyro() {
        int steps = 24;
        float r = 0.45f;
        List<Float> out = new ArrayList<>(steps * 18);
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2.0 / steps;
            double a1 = (i + 1) * Math.PI * 2.0 / steps;
            float c0 = (float) Math.cos(a0) * r;
            float s0 = (float) Math.sin(a0) * r;
            float c1 = (float) Math.cos(a1) * r;
            float s1 = (float) Math.sin(a1) * r;
            edge(out, c0, s0, 0, c1, s1, 0);
            edge(out, c0, 0, s0, c1, 0, s1);
            edge(out, 0, c0, s0, 0, c1, s1);
        }
        return fromList(out);
    }

    public static WireMesh proceduralGem() {
        int sides = 8;
        float girdleRadius = 0.44f;
        float tableRadius = 0.22f;
        float girdleY = 0.1f;
        float tableY = 0.34f;
        float tipY = -0.52f;
        List<Float> out = new ArrayList<>(sides * 36);
        float[] gx = new float[sides];
        float[] gz = new float[sides];
        float[] tx = new float[sides];
        float[] tz = new float[sides];
        for (int i = 0; i < sides; i++) {
            double angle = i * Math.PI * 2.0 / sides;
            gx[i] = (float) Math.cos(angle) * girdleRadius;
            gz[i] = (float) Math.sin(angle) * girdleRadius;
            tx[i] = (float) Math.cos(angle) * tableRadius;
            tz[i] = (float) Math.sin(angle) * tableRadius;
        }
        for (int i = 0; i < sides; i++) {
            int n = (i + 1) % sides;
            edge(out, gx[i], girdleY, gz[i], gx[n], girdleY, gz[n]);
            edge(out, tx[i], tableY, tz[i], tx[n], tableY, tz[n]);
            edge(out, gx[i], girdleY, gz[i], tx[i], tableY, tz[i]);
            edge(out, gx[i], girdleY, gz[i], 0.0f, tipY, 0.0f);
        }
        return fromList(out);
    }

    private static void edge(List<Float> out, float x0, float y0, float z0, float x1, float y1, float z1) {
        out.add(x0);
        out.add(y0);
        out.add(z0);
        out.add(x1);
        out.add(y1);
        out.add(z1);
    }

    private static WireMesh fromList(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return new WireMesh(arr);
    }

    private static WireMesh loadObj(InputStream in, boolean normalize, float yOffset) throws Exception {
        List<Vector3f> verts = new ArrayList<>();
        Map<Long, EdgeAcc> edges = new HashMap<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    float x = Float.parseFloat(p[1]);
                    float y = Float.parseFloat(p[2]) + yOffset;
                    float z = Float.parseFloat(p[3]);
                    verts.add(new Vector3f(x, y, z));
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                } else if (line.startsWith("f ")) {
                    String[] p = line.split("\\s+");
                    for (int i = 1; i < p.length; i++) {
                        int j = i == p.length - 1 ? 1 : i + 1;
                        int a = Integer.parseInt(p[i].split("/")[0]) - 1;
                        int b = Integer.parseInt(p[j].split("/")[0]) - 1;
                        if (a == b) {
                            continue;
                        }
                        int nIdx = -1;
                        String[] parts = p[i].split("/");
                        if (parts.length > 2 && !parts[2].isEmpty()) {
                            nIdx = Integer.parseInt(parts[2]) - 1;
                        }
                        long key = ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
                        EdgeAcc acc = edges.get(key);
                        if (acc == null) {
                            Vector3f va = verts.get(a);
                            Vector3f vb = verts.get(b);
                            edges.put(key, new EdgeAcc(va.x, va.y, va.z, vb.x, vb.y, vb.z, nIdx));
                        } else {
                            acc.count++;
                            if (acc.normal != nIdx) {
                                acc.boundary = false;
                            }
                        }
                    }
                }
            }
        }
        float cx = normalize ? (minX + maxX) * 0.5F : 0f;
        float cy = normalize ? (minY + maxY) * 0.5F : 0f;
        float cz = normalize ? (minZ + maxZ) * 0.5F : 0f;
        float scale = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float inv = normalize && scale > 1.0E-5F ? 1.0F / scale : 1.0F;
        List<Float> out = new ArrayList<>();
        for (EdgeAcc e : edges.values()) {
            if (e.count == 1 || !e.boundary) {
                out.add((e.x0 - cx) * inv);
                out.add((e.y0 - cy) * inv);
                out.add((e.z0 - cz) * inv);
                out.add((e.x1 - cx) * inv);
                out.add((e.y1 - cy) * inv);
                out.add((e.z1 - cz) * inv);
            }
        }
        return fromList(out);
    }

    private static WireMesh loadGltf(InputStream in) throws Exception {
        byte[] raw = in.readAllBytes();
        JsonObject root = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        String uri = root.getAsJsonArray("buffers").get(0).getAsJsonObject().get("uri").getAsString();
        int comma = uri.indexOf(',');
        byte[] bin = Base64.getDecoder().decode(uri.substring(comma + 1));
        ByteBuffer buf = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);

        JsonArray views = root.getAsJsonArray("bufferViews");
        int[] viewOff = new int[views.size()];
        for (int i = 0; i < views.size(); i++) {
            JsonObject v = views.get(i).getAsJsonObject();
            viewOff[i] = v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0;
        }
        JsonArray accessors = root.getAsJsonArray("accessors");
        int[] accView = new int[accessors.size()];
        int[] accCount = new int[accessors.size()];
        int[] accOff = new int[accessors.size()];
        int[] accComp = new int[accessors.size()];
        for (int i = 0; i < accessors.size(); i++) {
            JsonObject a = accessors.get(i).getAsJsonObject();
            accView[i] = a.get("bufferView").getAsInt();
            accCount[i] = a.get("count").getAsInt();
            accOff[i] = a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0;
            accComp[i] = a.get("componentType").getAsInt();
        }
        JsonArray nodes = root.getAsJsonArray("nodes");
        float[][] nodePos = new float[nodes.size()][3];
        boolean[] visited = new boolean[nodes.size()];
        int scene = root.has("scene") ? root.get("scene").getAsInt() : 0;
        JsonArray sceneNodes = root.getAsJsonArray("scenes").get(scene).getAsJsonObject().getAsJsonArray("nodes");
        for (int i = 0; i < sceneNodes.size(); i++) {
            walkNodes(nodes, sceneNodes.get(i).getAsInt(), 0, 0, 0, nodePos, visited);
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (!visited[i]) {
                walkNodes(nodes, i, 0, 0, 0, nodePos, visited);
            }
        }
        JsonArray meshes = root.getAsJsonArray("meshes");
        Map<Long, EdgeAcc> edges = new HashMap<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int ni = 0; ni < nodes.size(); ni++) {
            JsonObject node = nodes.get(ni).getAsJsonObject();
            if (!node.has("mesh")) {
                continue;
            }
            int meshIdx = node.get("mesh").getAsInt();
            JsonObject prim = meshes.get(meshIdx).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();
            int posAcc = prim.getAsJsonObject("attributes").get("POSITION").getAsInt();
            int idxAcc = prim.get("indices").getAsInt();
            int vCount = accCount[posAcc];
            int posBase = viewOff[accView[posAcc]] + accOff[posAcc];
            float[] pos = new float[vCount * 3];
            for (int v = 0; v < vCount; v++) {
                float x = buf.getFloat(posBase + v * 12) + nodePos[ni][0];
                float y = buf.getFloat(posBase + v * 12 + 4) + nodePos[ni][1];
                float z = buf.getFloat(posBase + v * 12 + 8) + nodePos[ni][2];
                pos[v * 3] = x;
                pos[v * 3 + 1] = y;
                pos[v * 3 + 2] = z;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            int iCount = accCount[idxAcc];
            int idxBase = viewOff[accView[idxAcc]] + accOff[idxAcc];
            int comp = accComp[idxAcc];
            for (int t = 0; t + 2 < iCount; t += 3) {
                int i0, i1, i2;
                if (comp == 5125) {
                    i0 = buf.getInt(idxBase + t * 4);
                    i1 = buf.getInt(idxBase + (t + 1) * 4);
                    i2 = buf.getInt(idxBase + (t + 2) * 4);
                } else {
                    i0 = buf.getShort(idxBase + t * 2) & 0xFFFF;
                    i1 = buf.getShort(idxBase + (t + 1) * 2) & 0xFFFF;
                    i2 = buf.getShort(idxBase + (t + 2) * 2) & 0xFFFF;
                }
                addEdgeWorld(edges, pos, i0, i1);
                addEdgeWorld(edges, pos, i1, i2);
                addEdgeWorld(edges, pos, i2, i0);
            }
        }
        if (edges.isEmpty() || !Float.isFinite(minX)) {
            return proceduralHeart();
        }
        float cx = (minX + maxX) * 0.5F;
        float cy = (minY + maxY) * 0.5F;
        float cz = (minZ + maxZ) * 0.5F;
        float scale = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float inv = scale > 1.0E-5F ? 1.0F / scale : 1.0F;
        List<Float> out = new ArrayList<>(edges.size() * 6);
        for (EdgeAcc e : edges.values()) {
            out.add((e.x0 - cx) * inv);
            out.add((e.y0 - cy) * inv);
            out.add((e.z0 - cz) * inv);
            out.add((e.x1 - cx) * inv);
            out.add((e.y1 - cy) * inv);
            out.add((e.z1 - cz) * inv);
        }
        if (out.isEmpty()) {
            return proceduralHeart();
        }
        return fromList(out);
    }

    public static WireMesh proceduralHeart() {
        int segs = 48;
        List<Float> out = new ArrayList<>(segs * 12);
        float[] xs = new float[segs];
        float[] ys = new float[segs];
        for (int i = 0; i < segs; i++) {
            double t = i * (Math.PI * 2.0) / segs;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            xs[i] = (float) (x / 34.0);
            ys[i] = (float) (y / 34.0);
        }
        float depth = 0.12f;
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % segs;
            edge(out, xs[i], ys[i], depth, xs[j], ys[j], depth);
            edge(out, xs[i], ys[i], -depth, xs[j], ys[j], -depth);
            if (i % 3 == 0) {
                edge(out, xs[i], ys[i], depth, xs[i], ys[i], -depth);
            }
        }
        return fromList(out);
    }

    private static void addEdge(Map<Long, EdgeAcc> edges, float[] pos, int a, int b) {
        if (a == b) {
            return;
        }
        long key = ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
        EdgeAcc acc = edges.get(key);
        if (acc == null) {
            edges.put(key, new EdgeAcc(
                    pos[a * 3], pos[a * 3 + 1], pos[a * 3 + 2],
                    pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2], -1));
        } else {
            acc.count++;
        }
    }

    private static void addEdgeWorld(Map<Long, EdgeAcc> edges, float[] pos, int a, int b) {
        if (a == b || a < 0 || b < 0) {
            return;
        }
        float ax = pos[a * 3], ay = pos[a * 3 + 1], az = pos[a * 3 + 2];
        float bx = pos[b * 3], by = pos[b * 3 + 1], bz = pos[b * 3 + 2];
        long ka = quantKey(ax, ay, az);
        long kb = quantKey(bx, by, bz);
        if (ka == kb) {
            return;
        }
        long lo = Math.min(ka, kb);
        long hi = Math.max(ka, kb);
        long key = lo * 0x9E3779B97F4A7C15L + hi;
        EdgeAcc acc = edges.get(key);
        if (acc == null) {
            edges.put(key, new EdgeAcc(ax, ay, az, bx, by, bz, -1));
        } else {
            acc.count++;
        }
    }

    private static long quantKey(float x, float y, float z) {
        int ix = Math.round(x * 64f);
        int iy = Math.round(y * 64f);
        int iz = Math.round(z * 64f);
        return (((long) ix & 0x1FFFFF) << 42) | (((long) iy & 0x1FFFFF) << 21) | ((long) iz & 0x1FFFFF);
    }

    private static void walkNodes(JsonArray nodes, int idx, float ox, float oy, float oz, float[][] out, boolean[] visited) {
        if (idx < 0 || idx >= nodes.size() || visited[idx]) {
            return;
        }
        visited[idx] = true;
        JsonObject node = nodes.get(idx).getAsJsonObject();
        float x = ox, y = oy, z = oz;
        if (node.has("translation")) {
            JsonArray t = node.getAsJsonArray("translation");
            x += t.get(0).getAsFloat();
            y += t.get(1).getAsFloat();
            z += t.get(2).getAsFloat();
        }
        out[idx][0] = x;
        out[idx][1] = y;
        out[idx][2] = z;
        if (node.has("children")) {
            JsonArray ch = node.getAsJsonArray("children");
            for (int i = 0; i < ch.size(); i++) {
                walkNodes(nodes, ch.get(i).getAsInt(), x, y, z, out, visited);
            }
        }
    }

    private static final class EdgeAcc {
        final float x0, y0, z0, x1, y1, z1;
        final int normal;
        int count = 1;
        boolean boundary = true;

        EdgeAcc(float x0, float y0, float z0, float x1, float y1, float z1, int normal) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.normal = normal;
        }
    }
}
