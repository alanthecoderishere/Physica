#version 410 core
out vec4 FragColor;

in vec3 FragPos;

uniform vec3 color;

void main() {
    // Compute face normals using derivatives
    vec3 xTangent = dFdx(FragPos);
    vec3 yTangent = dFdy(FragPos);
    vec3 normal = normalize(cross(xTangent, yTangent));
    
    // Simple directional light
    vec3 lightDir = normalize(vec3(0.5, 1.0, 0.3));
    float diff = max(dot(normal, lightDir), 0.0);
    
    vec3 ambient = 0.4 * color;
    vec3 diffuse = diff * color * 0.6;
    
    FragColor = vec4(ambient + diffuse, 1.0);
}
