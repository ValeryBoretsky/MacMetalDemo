#include <metal_stdlib>
using namespace metal;

enum class RainPhase : uint {
    ADMIT_RAIN = 0,
    GRAVITY = 1,
    RUNOFF = 2,
    DRAIN = 3,
};

struct RainConstants {
    uint width;
    uint height;
    uint rainMass;
    RainPhase phase;
    uint parity;
};

constant int cellCapacity = 256;

int horizontalTransfer(device const float *old, device const float *walls, uint left, uint right, uint width) {
    const int a = int(old[left]);
    const int b = int(old[right]);
    const float leftSupport = walls[left + width];
    const float rightSupport = walls[right + width];
    if (leftSupport >= 2.0f || rightSupport >= 2.0f) {
        const bool toRight = leftSupport == 2.0f;
        const bool toLeft = rightSupport == 3.0f;
        if (toRight == toLeft) return 0;
        return toRight ? min(a, cellCapacity - b) : -min(b, cellCapacity - a);
    }
    const uint donorBase = (a > b ? left : right) + width;
    if (walls[donorBase] == 0.0f && old[donorBase] != float(cellCapacity)) return 0;
    return (a - b) / 2;
}

kernel void rainStep(device const float *old [[buffer(0)]],
                     device float *next [[buffer(1)]],
                     device const float *walls [[buffer(2)]],
                     device uint *admitted [[buffer(3)]],
                     device uint *drained [[buffer(4)]],
                     device const float *dropCounts [[buffer(5)]],
                     constant RainConstants &settings [[buffer(6)]],
                     uint index [[thread_position_in_grid]]) {
    const uint width = settings.width;
    const uint height = settings.height;
    if (index >= width * height) return;
    if (walls[index] != 0.0f) {
        next[index] = 0.0f;
        return;
    }
    const uint x = index % width;
    const uint y = index / width;
    const int mass = int(old[index]);
    int result = mass;

    if (settings.phase == RainPhase::ADMIT_RAIN && y == 0) {
        const int accepted = min(cellCapacity - mass, int(dropCounts[x]) * int(settings.rainMass));
        result += accepted;
        admitted[x] += uint(accepted);
    } else if (settings.phase == RainPhase::GRAVITY) {
        // Both cells calculate a shared face from the same immutable masses.
        const int incoming = y > 0 && walls[index - width] == 0.0f
            ? min(int(old[index - width]), cellCapacity - mass) : 0;
        const int outgoing = y + 1 < height && walls[index + width] == 0.0f
            ? min(mass, cellCapacity - int(old[index + width])) : 0;
        result += incoming - outgoing;
    } else if (settings.phase == RainPhase::RUNOFF && y + 1 < height && x >= settings.parity) {
        const uint left = ((x - settings.parity) & 1u) == 0 ? x : x - 1;
        const uint right = left + 1;
        const uint row = y * width;
        if (right < width && walls[row + left] == 0.0f && walls[row + right] == 0.0f) {
            const int transfer = horizontalTransfer(old, walls, row + left, row + right, width);
            result += x == left ? -transfer : transfer;
        }
    } else if (settings.phase == RainPhase::DRAIN && y + 1 == height) {
        drained[x] += uint(mass);
        result = 0;
    }
    next[index] = float(result);
}
