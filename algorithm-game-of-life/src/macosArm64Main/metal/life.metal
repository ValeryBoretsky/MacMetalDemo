#include <metal_stdlib>
using namespace metal;

kernel void lifeStep(device const float *old [[buffer(0)]],
                     device float *next [[buffer(1)]],
                     constant uint2 &size [[buffer(2)]],
                     uint index [[thread_position_in_grid]]) {
    const uint width = size.x;
    const uint height = size.y;
    if (index >= width * height) return;

    const uint x = index % width;
    const uint y = index / width;
    const uint left = x == 0 ? width - 1 : x - 1;
    const uint right = x + 1 == width ? 0 : x + 1;
    const uint above = (y == 0 ? height - 1 : y - 1) * width;
    const uint row = y * width;
    const uint below = (y + 1 == height ? 0 : y + 1) * width;
    const uint neighbors = uint(old[above + left]) + uint(old[above + x]) + uint(old[above + right])
                         + uint(old[row + left])                         + uint(old[row + right])
                         + uint(old[below + left]) + uint(old[below + x]) + uint(old[below + right]);
    next[index] = neighbors == 3 || (neighbors == 2 && old[index] == 1.0f) ? 1.0f : 0.0f;
}
