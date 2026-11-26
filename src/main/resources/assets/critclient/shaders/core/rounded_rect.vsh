#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in float LineWidth;

out vec4 fillColor;
out vec2 localPosition;
flat out vec2 size;
flat out vec4 edgeColor;
flat out float radius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fillColor = Color;
    localPosition = UV0;
    size = vec2(UV1);
    uvec2 argb = uvec2(UV2) & 65535u;
    edgeColor = vec4(uvec4(argb.x & 255u, argb.y >> 8u, argb.y & 255u, argb.x >> 8u)) / 255.0;
    radius = LineWidth;
}
