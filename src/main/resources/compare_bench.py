import csv
import re, sys

def parse_results(filename):
    results = {}
    with open(filename) as f:
        in_final = False
        for line in f:
            if 'Final Results' in line:
                in_final = True
                continue
            if in_final and line.strip() and '=====' not in line:
                parts = line.split()
                if len(parts) >= 4:
                    results[(parts[0], parts[1])] = (parts[2], parts[3])
    return results

def parse_ms(s):
    return float(s[:-2]) if s.endswith('ms') else None

base = '/Users/apete/Developer/optimatika_git/ojAlgo-mathematical-programming-benchmark/src/main/resources'
old = parse_results(f'{base}/console.log')
new = parse_results(f'{base}/new_console.log')

# Parse CSV for problem characteristics
prob_info = {}
with open(f'{base}/benchmark_output.csv') as f:
    reader = csv.DictReader(f, delimiter='\t')
    for row in reader:
        model = row['Model']
        if model not in prob_info:
            prob_info[model] = {
                'nbVars': int(row['nbVars']),
                'nbExpr': int(row['nbExpr']),
                'density': float(row['density'])
            }

# dual-S analysis with problem characteristics
print("=== dual-S (uses SparseLU / ForrestTomlin) ===")
print(f"{'Model':<14} {'nbExpr':>6} {'density':>8} {'Old(ms)':>11} {'New(ms)':>11} {'Ratio':>7} {'Delta':>8}")
print('-' * 72)

rows = []
for key in sorted(old.keys()):
    model, solver = key
    if 'dual-S' not in solver or key not in new:
        continue
    o_status, o_time = old[key]
    n_status, n_time = new[key]
    if o_status != 'OPTIMAL' or n_status != 'OPTIMAL':
        continue
    o_ms, n_ms = parse_ms(o_time), parse_ms(n_time)
    if o_ms and n_ms and model in prob_info:
        info = prob_info[model]
        ratio = n_ms / o_ms
        delta = (n_ms - o_ms) / o_ms * 100
        rows.append((model, info['nbExpr'], info['density'], o_ms, n_ms, ratio, delta))

# Sort by density to see the pattern
rows.sort(key=lambda r: r[2])

sparse_ratios = []
dense_ratios = []
small_ratios = []
large_ratios = []

for model, nbExpr, density, o_ms, n_ms, ratio, delta in rows:
    marker = '<' if ratio < 0.97 else '>' if ratio > 1.03 else '='
    print(f"{model:<14} {nbExpr:>6} {density:>8.4f} {o_ms:>11.2f} {n_ms:>11.2f} {ratio:>6.3f}x {delta:>+7.1f}% {marker}")
    if density < 0.1:
        sparse_ratios.append(ratio)
    else:
        dense_ratios.append(ratio)
    if nbExpr < 700:
        small_ratios.append(ratio)
    else:
        large_ratios.append(ratio)

def geo_mean(lst):
    if not lst:
        return 0
    g = 1
    for r in lst: g *= r
    return g ** (1/len(lst))

print()
print("--- By density ---")
print(f"  Sparse (density < 0.1): n={len(sparse_ratios)}, geo_mean={geo_mean(sparse_ratios):.3f}")
print(f"  Dense  (density >= 0.1): n={len(dense_ratios)}, geo_mean={geo_mean(dense_ratios):.3f}")
print()
print("--- By constraint count (nbExpr = basis size m) ---")
print(f"  Small (m < 700): n={len(small_ratios)}, geo_mean={geo_mean(small_ratios):.3f}")
print(f"  Large (m >= 700): n={len(large_ratios)}, geo_mean={geo_mean(large_ratios):.3f}")

# Also show dual-D as control
print()
print("=== dual-D (control — no SparseLU / ForrestTomlin) ===")
print(f"{'Model':<14} {'nbExpr':>6} {'density':>8} {'Old(ms)':>11} {'New(ms)':>11} {'Ratio':>7} {'Delta':>8}")
print('-' * 72)

d_rows = []
for key in sorted(old.keys()):
    model, solver = key
    if 'dual-D' not in solver or key not in new:
        continue
    o_status, o_time = old[key]
    n_status, n_time = new[key]
    if o_status != 'OPTIMAL' or n_status != 'OPTIMAL':
        continue
    o_ms, n_ms = parse_ms(o_time), parse_ms(n_time)
    if o_ms and n_ms and model in prob_info:
        info = prob_info[model]
        ratio = n_ms / o_ms
        delta = (n_ms - o_ms) / o_ms * 100
        d_rows.append((model, info['nbExpr'], info['density'], o_ms, n_ms, ratio, delta))

d_rows.sort(key=lambda r: r[2])
d_sparse = []
d_dense = []

for model, nbExpr, density, o_ms, n_ms, ratio, delta in d_rows:
    marker = '<' if ratio < 0.97 else '>' if ratio > 1.03 else '='
    print(f"{model:<14} {nbExpr:>6} {density:>8.4f} {o_ms:>11.2f} {n_ms:>11.2f} {ratio:>6.3f}x {delta:>+7.1f}% {marker}")
    if density < 0.1:
        d_sparse.append(ratio)
    else:
        d_dense.append(ratio)

print()
print("--- dual-D by density ---")
print(f"  Sparse (density < 0.1): n={len(d_sparse)}, geo_mean={geo_mean(d_sparse):.3f}")
print(f"  Dense  (density >= 0.1): n={len(d_dense)}, geo_mean={geo_mean(d_dense):.3f}")