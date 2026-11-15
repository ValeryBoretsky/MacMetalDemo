#include <metal_stdlib>
using namespace metal;
struct Constants { uint count; float gravity; float dt; float epsilonSquared; };
kernel void nbodyStep(device const float4 *positions [[buffer(0)]],
                      device const float4 *velocities [[buffer(1)]],
                      device float4 *outputPositions [[buffer(2)]],
                      device float4 *outputVelocities [[buffer(3)]],
                      constant Constants &c [[buffer(4)]], uint i [[thread_position_in_grid]]) {
    if (i >= c.count) return;
    // Each position float4 stores x, y, z, and mass.
    // Each velocity float4 stores x, y, z, and zero padding.
    float4 p = positions[i];
    float3 a = float3(0.0f);
    for (uint j = 0; j < c.count; j++) {
        if (j == i) continue;
        float3 d = positions[j].xyz - p.xyz;
        // Softening prevents a singular force when positions coincide.
        float r2 = d.x*d.x + d.y*d.y + d.z*d.z + c.epsilonSquared;
        float factor = c.gravity * positions[j].w / (r2 * sqrt(r2));
        a += factor * d;
    }
    // Semi-implicit Euler updates velocity before it updates position.
    float3 v = velocities[i].xyz + a * c.dt;
    outputPositions[i] = float4(p.xyz + v * c.dt, p.w);
    outputVelocities[i] = float4(v, 0.0f);
}
