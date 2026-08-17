import math
import numpy as np

# 1. WPF ElasticEase implementation (.NET Reference Source)
# https://referencesource.microsoft.com/#PresentationCore/Core/CSharp/System/Windows/Media/Animation/ElasticEase.cs
def wpf_elastic_ease_in(normalized_time, oscillations=1, springiness=7.0):
    # WPF ElasticEase formula:
    # double num = Math.Max(0.0, Oscillations);
    # double num2 = Math.Max(0.0, Springiness);
    # if (num2 == 0.0) { return normalized_time; }
    # double num3 = (Math.Exp(num2 * normalized_time) - 1.0) / (Math.Exp(num2) - 1.0);
    # return num3 * Math.Sin((2.0 * Math.PI * num + Math.PI / 2.0) * normalized_time);
    if springiness == 0.0:
        return normalized_time
    num3 = (math.exp(springiness * normalized_time) - 1.0) / (math.exp(springiness) - 1.0)
    return num3 * math.sin((2.0 * math.pi * oscillations + math.pi / 2.0) * normalized_time)

def wpf_elastic_ease_out(normalized_time, oscillations=1, springiness=7.0):
    return 1.0 - wpf_elastic_ease_in(1.0 - normalized_time, oscillations, springiness)

# 2. Compose Spring simulation
def compose_spring(t, damping_ratio=0.65, stiffness=300.0, target=1.0):
    # m = 1, k = stiffness, c = 2 * damping_ratio * sqrt(k)
    w0 = math.sqrt(stiffness)
    if damping_ratio < 1.0: # underdamped
        wd = w0 * math.sqrt(1.0 - damping_ratio**2)
        decay = math.exp(-damping_ratio * w0 * t)
        # x(t) = target - decay * (cos(wd*t) + (zeta*w0/wd)*sin(wd*t))
        val = target - decay * (math.cos(wd * t) + (damping_ratio * w0 / wd) * math.sin(wd * t))
        return val
    return 0.0

# 3. WPF BackEase implementation (.NET Reference Source)
# https://referencesource.microsoft.com/#PresentationCore/Core/CSharp/System/Windows/Media/Animation/BackEase.cs
def wpf_back_ease_in(normalized_time, amplitude=1.0):
    # normalized_time^3 - normalized_time * amplitude * sin(normalized_time * pi)
    return math.pow(normalized_time, 3.0) - normalized_time * amplitude * math.sin(normalized_time * math.pi)

def wpf_back_ease_out(normalized_time, amplitude=1.0):
    return 1.0 - wpf_back_ease_in(1.0 - normalized_time, amplitude)

# 4. DockCardPhysics.PopInEase from Plan (Section 4.2 L997-1001)
def plan_pop_in_ease(fraction):
    t = fraction - 1.0
    a = 3.53
    return 1.0 + t * t * ((a + 1.0) * t + a)

# 5. DockCardPhysics.ContractEase from Plan (Section 4.2 L1007-1011)
def plan_contract_ease(fraction):
    t = fraction - 1.0
    a = 0.15
    return 1.0 + t * t * ((a + 1.0) * t + a)

# 6. CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f) for HoverEase
def cubic_bezier(p0, p1, p2, p3, t):
    return (1-t)**3 * p0 + 3*(1-t)**2 * t * p1 + 3*(1-t) * t**2 * p2 + t**3 * p3

def solve_bezier_x(p1x, p2x, x_target, tol=1e-5):
    # Newton-Raphson to find t for given x
    t = x_target
    for _ in range(10):
        x = 3*(1-t)**2 * t * p1x + 3*(1-t) * t**2 * p2x + t**3
        dx = 3*(1-t)**2 * p1x + 6*(1-t)*t*(p2x - p1x) + 3*t**2*(1 - p2x)
        if abs(x - x_target) < tol:
            break
        if abs(dx) < 1e-6:
            break
        t = t - (x - x_target)/dx
        t = max(0.0, min(1.0, t))
    return t

def plan_hover_ease(x):
    t = solve_bezier_x(0.34, 0.64, x)
    # y = cubic_bezier(0, 1.56, 1.0, 1.0, t)
    return 3*(1-t)**2 * t * 1.56 + 3*(1-t) * t**2 * 1.0 + t**3

print("=== ElasticEase(1, 7) (800ms) vs Compose Spring(damping=0.65, stiffness=300) ===")
duration = 0.8
for ms in range(0, 801, 100):
    t_norm = ms / 800.0
    t_sec = ms / 1000.0
    wpf_val = wpf_elastic_ease_out(t_norm, oscillations=1, springiness=7.0)
    spring_val = compose_spring(t_sec, damping_ratio=0.65, stiffness=300.0)
    print(f"t={ms:3d}ms (norm={t_norm:.2f}): WPF ElasticEase={wpf_val:+.4f} | Compose Spring={spring_val:+.4f} | Diff={abs(wpf_val - spring_val):.4f}")

print("\n=== BackEase(0.15) vs plan_contract_ease ===")
for ms in range(0, 101, 10):
    t_norm = ms / 100.0
    wpf_back = wpf_back_ease_out(t_norm, amplitude=0.15)
    plan_back = plan_contract_ease(t_norm)
    print(f"t_norm={t_norm:.2f}: WPF BackEase(0.15)={wpf_back:+.4f} | Plan ContractEase={plan_back:+.4f} | Diff={abs(wpf_back - plan_back):.4f}")

print("\n=== BackEase(3.53) vs plan_pop_in_ease ===")
for ms in range(0, 101, 10):
    t_norm = ms / 100.0
    wpf_back = wpf_back_ease_out(t_norm, amplitude=3.53)
    plan_back = plan_pop_in_ease(t_norm)
    print(f"t_norm={t_norm:.2f}: WPF BackEase(3.53)={wpf_back:+.4f} | Plan PopInEase={plan_back:+.4f} | Diff={abs(wpf_back - plan_back):.4f}")

print("\n=== BackEase(1.22) vs plan_hover_ease ===")
for ms in range(0, 101, 10):
    t_norm = ms / 100.0
    wpf_back = wpf_back_ease_out(t_norm, amplitude=1.22)
    plan_hover = plan_hover_ease(t_norm)
    print(f"t_norm={t_norm:.2f}: WPF BackEase(1.22)={wpf_back:+.4f} | Plan HoverEase={plan_hover:+.4f} | Diff={abs(wpf_back - plan_hover):.4f}")
