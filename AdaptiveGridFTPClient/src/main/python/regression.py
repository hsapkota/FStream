import numpy as np
from scipy.optimize import curve_fit
import argparse
import os
from scipy import stats
import matplotlib.pyplot as plt
import random as rd

method_ = ""
count_ = 1
isLinearRegression = False
def fit_function(x, a, b):
    return b + (1.0 * a/np.power(x ,1)) # y = a + b/x
def regression_line(x, a, b):
    return a * np.power(x, 1) + b #, 1/3 y = ax + b
def regression_line_cuberoot(x, a, b):
    return a * np.power(x, 1/3) + b #, 1/3  y = a * cuberoot(x) + b
def fit_function_opposite(x, a, b):
    return ((a/(x + b)) ** 2) ** (1/5)
def fit_func(x, a, b):
    if isLinearRegression:
        return b + (1.0 * a/(x**2))
    else:
        return a * x + b
max_cc = 30
url = "./inst-throughput.txt"
def get_xy(url):
    x = []
    y = []
    curr_time = []
    with open(url, "r") as f:
        line = f.readline()
        while line:
            vals = line.split("\t")
            time_ = float(vals[0])
            cc_ = float(vals[1])
            thpt_ = float(vals[2])
            if cc_ == 0.:
                line = f.readline()
                continue
            x.append(cc_)
            y.append(thpt_)
            curr_time.append(time_)
            line = f.readline()
    return curr_time, x, y
def get_uniform_count(x, y, n_x, n_y):
    data_dict = {}
    every_count = 5
    for i in range(len(x)):
        data_dict[x[i]] = y[i]
    data_count_range = {}
    min_x = int(min(x))
    max_x = int(max(x))
    data_counter = {}
    counter_y = []
    for i in range(min_x, max_x + 1, every_count):
        data_counter[i] = 0
        data_count_range[i] = {}
        for j in range(i, i+every_count):
            if j in data_dict:
                data_count_range[i][j] = data_dict[j]
                data_counter[i] += 1
        counter_y.append(data_counter[i])
    new_x = []
    new_y = []
    for k in range(len(list(data_count_range))):
        i = list(data_count_range)[k] #int(k)+1
        _x = sorted(rd.sample(list(data_count_range[i]), min(3, len(data_count_range[i]))))
        for j in _x:
            new_x.append(j)
            new_y.append(data_count_range[i][j])
            # print("Count starting %d is x: %d , y: %d" % (i, int(j), int(data_count_range[i][j])))
    return new_x, new_y

def get_kmeans(x, y):
    global count_
    count_to_choose = 1000000
    dict_ = {}
    for i in range(len(x)):
        if x[i] not in dict_:
            dict_[x[i]] = []
        dict_[x[i]].append(y[i])
    for i in dict_:
        count_to_choose = min(count_to_choose, len(dict_[i]))
    tmp_y_1 = []
    tmp_y = []
    for y_index in dict_:
        tmp_y = dict_[y_index]
        new_tmp_y = []
        count_run = 0
        while len(tmp_y) > count_to_choose and count_run < count_:
            count_run += 1
            z = list(np.abs(stats.zscore(tmp_y)))
            for i in range(len(z)):
                if z[i] <= 1:
                    new_tmp_y.append(tmp_y[i])
            tmp_y = new_tmp_y
            new_tmp_y = []
        dict_[y_index] = tmp_y
    x, y = [], []
    for i in dict_:
        if len(dict_[i]) >= 1:
            x.append(i)
            y.append(1.0 * sum(dict_[i])/len(dict_[i]))
    return x, y
def get_cc(thpt, a, b, fcn):
    global max_cc
    close_to_thpt = 2**31
    close_cc = 0
    previous_thpt = 0.0
    previous_sign = 0
    # if regression_line != fcn:
    for i in range(1, max_cc+1):
        val_ = fcn(i, a, b)
        tmp_diff = abs(thpt - val_)
        # print(abs(val_ - previous_thpt)/val_, val_, previous_thpt)
        if abs(thpt - val_) < close_to_thpt:
            if val_ == 0 or abs(abs(val_ - previous_thpt)/val_) > 0.01:
                close_to_thpt = abs(thpt - val_)
                close_cc = i
                previous_thpt = val_
            else:
                break
        else:
            break
    # else:
    #     for i in range(1, max_cc+1):
    #         val_ = fcn(i, a, b)
    #         print(abs(abs(val_ - previous_thpt)/val_), val_, previous_thpt)
    #         if abs(thpt - val_) < close_to_thpt:
    #             close_to_thpt = abs(thpt - val_)
    #             close_cc = i
    #             previous_thpt = val_
    return close_cc
    
def get_curve_fit(cc, type_="", isOpposite=False):
    global url
    n_curr_time, x, y = get_xy(url)

    # last_time = n_curr_time[-1]
    # start_index = -125
    # if last_time > 500:
    #     for i in range(len(n_curr_time), -1, -1):
    #         if n_curr_time[i] < last_time-500:
    #             start_index = i
    #             break
    # curr_time, x, y = n_curr_time[start_index:], n_x[start_index:], n_y[start_index:]

    # print(x)
    if len(x) < 10:
        return 1
    x, y = get_kmeans(x, y)
    x, y = get_uniform_count(x, y, x, y)
    if "curve" in type_:
        if not isOpposite:
            params = curve_fit(fit_function, x, y)
            [a, b] = params[0]
            return fit_function(cc, a, b) #fit_function
        else:
            params = curve_fit(fit_function, x, y)
            [a, b] = params[0]
            # plot_graph(a, b, fit_function, x, y)
            # print(a, b)
            
            return get_cc(cc, a, b, fit_function)
    elif "linear" in type_:
        if not isOpposite:
            params = curve_fit(regression_line, x, y)
            [a, b] = params[0]
            return regression_line(cc, a, b)
        else:
            params = curve_fit(regression_line, x, y)
            [a, b] = params[0]
            # plot_graph(a, b, regression_line, x, y)
            return get_cc(cc, a, b, regression_line)
    elif "cube" in type_:
        if isOpposite:
            params = curve_fit(regression_line_cuberoot, x, y)
            [a, b] = params[0]
            # plot_graph(a, b, regression_line_cuberoot, x, y)
            return get_cc(cc, a, b, regression_line_cuberoot)

def plot_graph(a, b, fcn, x_, y_):
    global method_, count_, max_cc
    x = list(range(1, max_cc))
    y = list(fcn(x, a, b))
    plt.plot(x, y, label="Model", marker='x')
    plt.scatter(x_, y_, label="Actual", c="orange")
    plt.xlabel("CC")
    plt.ylabel("Throughput")
    plt.ylim(0, max(y_))
    plt.legend()
    # plt.savefig("/Users/hem/Desktop/" + method_ + "_act_fig_" + str(count_) + ".png")
    plt.show()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Process some integers.')
    parser.add_argument('--cc', type=int,
                    help='CC value')
    parser.add_argument('--thpt', type=int,
                    help='Throughtput value')
    parser.add_argument('--method', type=str,
                    help='Throughtput value')
    parser.add_argument('--max_cc', type=int,
                    help='max_cc value')
    args = parser.parse_args()
    if args.max_cc:
        max_cc = args.max_cc
    if args.cc:
        if args.method:
            method_ = args.method
            if args.method == "curve":
                print(int(get_curve_fit(args.cc, args.method)))
                exit(0)
            if args.method == "linear":
                print(int(get_curve_fit(args.cc, args.method)))
                exit(0)
        print(int(get_curve_fit(args.cc, args.method)))
        exit(0)
    if args.thpt:
        if args.method:
            method_ = args.method
            if args.method == "linear":
                print(int(get_curve_fit(args.thpt, args.method, True)))
                exit(0)
            if args.method == "oppoiste_curve":
                print(int(get_curve_fit(args.thpt, args.method, True)))
                exit(0)
            if args.method == "cube_root":
                print(int(get_curve_fit(args.thpt, args.method, True)))
                exit(0)
                

        print(int(get_curve_fit(args.thpt, args.method, True)))
        exit(0)





