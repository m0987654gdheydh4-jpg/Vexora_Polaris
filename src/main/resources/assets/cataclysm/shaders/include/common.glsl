float rdist(vec2 pos, vec2 size, vec4 radius) {
    radius = min(radius, vec4(size.x, size.y, size.x, size.y));
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

float ralpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 center = size * 0.5;
    float feather = max(smoothness, 0.001);
    float dist = rdist(center - (coord * size), max(center - 1.0, vec2(0.0)), max(radius, vec4(0.0)));
    return 1.0 - smoothstep(1.0 - feather, 1.0, dist);
}

const vec2[4] RECT_VERTICES_COORDS = vec2[] (
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

vec2 rvertexcoord(int id) {
    return RECT_VERTICES_COORDS[id % 4];
}
