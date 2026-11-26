#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 fillColor;
in vec2 localPosition;
flat in vec2 size;
flat in vec4 edgeColor;
flat in float radius;

out vec4 fragColor;

void main() {
    float r = abs(radius);
    vec2 q = abs(localPosition - size * 0.5) - (size * 0.5 - r);
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    // The derivative gives the width of a single physical pixel in local coordinates.
    float aa = max(0.0001, fwidth(distance) * 0.5);
    float cover = 1.0 - smoothstep(-aa, aa, distance);
    if (radius < 0.0) {
        fragColor = vec4(fillColor.rgb, fillColor.a * (1.0 - cover)) * ColorModulator;
    } else {
        float inside = 1.0 - smoothstep(-aa, aa, distance + 1.0);
        float alpha = mix(edgeColor.a, fillColor.a, inside);
        // Fill and rim are disjoint, even when the panel opacity is turned down.
        vec3 rgb = mix(edgeColor.rgb * edgeColor.a, fillColor.rgb * fillColor.a, inside) / max(alpha, 0.0001);
        fragColor = vec4(rgb, alpha * cover) * ColorModulator;
    }
    if (fragColor.a == 0.0) discard;
}
