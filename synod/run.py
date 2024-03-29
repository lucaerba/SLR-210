import subprocess
import os
# Define the parameters to experiment with
N_values = [3, 10, 100]
f_values = [1, 4, 49]
alpha_values = [0, 0.1, 1]
tle_values = [200, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000, 2100]

def run_java():
    #remove and create system.log file
    if os.path.exists("system.log"):
        os.remove("system.log")
        print("system.log file removed")
    else:
        print("system.log file does not exist")

    #Repeat the experiment 5 times
    # Loop over all combinations of parameters
    for j in range(5):
        #print on system.log file the number of the experiment
        with open("system.log", "a") as file:
            file.write(f"Experiment number: {j}\n")

        for i in range(len(N_values)):
            for alpha in alpha_values:
                for tle in tle_values:
                    # Construct the arguments as a single string
                    # Run the Java program with the current parameters using Maven
                    command = ["mvn", "exec:exec", f"-DN={N_values[i]}", f"-Df={f_values[i]}",f"-Dalpha={alpha}",f"-Dtle={tle}"]
                    print("Running command:", " ".join(command))
                    #run them sequentially not in parallel
                    subprocess.run(command)

run_java()

import re
# Initialize parameters_list and times_list as lists of empty lists
parameters_list = []
times_list = [[] for _ in range(5)]
first = True
n_exp = 0
#do the mean over the 5 experiments
with open("system.log", "r") as file:
    for line in file:
        # Search for execution parameters and save everything in a dictionary
        # 19:51:45.098 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with N=100
        #19:51:45.099 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with tle=2000
        #19:51:45.099 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with f=49
        #19:51:45.100 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with alpha=0.0
        match = re.search(r'Experiment number: (\d+)', line)
        if match:
            print(f"Experiment number: {match.group(1)}")
            n_exp = int(match.group(1))
            times_list[n_exp] = []
        
        if(n_exp == 0):
            match = re.search(r'System started with N=(\d+)', line)
            if match:
                parameters = {}
                parameters['N'] = int(match.group(1))

            match = re.search(r'System started with tle=(\d+)', line)
            if match:
                parameters['tle'] = int(match.group(1))

            match = re.search(r'System started with f=(\d+)', line)
            if match:
                parameters['f'] = int(match.group(1))

            match = re.search(r'System started with alpha=(\d+\.\d+)', line)
            if match:
                parameters['alpha'] = float(match.group(1))
                parameters_list.append(parameters)
                print(f"Parameters: {parameters}")

        # Search for the lowest decision time
        match = re.search(r'time: (\d+)', line)
        if match:
            if first:
                print(f"First decision time: {match.group(1)}")
                first = False
                time_min = int(match.group(1))
            else:
                time_min = min(time_min, int(match.group(1)))
            print(f"Decision time: {match.group(1)}")

        match = re.search(r'System is shutting down...', line)
        if match:
            first = True
            times_list[n_exp].append(time_min)

#average the times of the 5 experiments, to get the mean time of each experiment parameter, each time_list has 5 arrays, one for each run
#so take the mean of the elements in the same position of each array and save it in a new array
times_list = [sum(x) / len(x) for x in zip(*times_list)]
print("Parameters list:", parameters_list)
print("Times list:", times_list)




#plot the data using the data collected
#Your goal is to find out how the latency depends on N (for a fixed tle) and tle (for a fixed N). Also try to see if the probability of failures α (for fixed N and tle) affects the latency.
#• As a baseline, you can consider N = 3, 10, 100 (with f = 1, 4, 49, respectively), α = 0, 0.1, 1.
#Also you should select several values of tle in a small enough range to capture the effect of leader election. Notice that if tle is chosen to be too large, it is every likely that some process will decide before that due to a transient absence of contention.
#So the right strategy would be to start with a large tle and then gradually decrease it until no decision is observed.

import matplotlib.pyplot as plt
import pandas as pd

# Convert parameters_list and times_list to a pandas DataFrame
df = pd.DataFrame(parameters_list)
df['time'] = times_list

print(df)

# Create a figure with 2 subplots
fig, axs = plt.subplots(2, 1, figsize=(10, 10))

# Define the baseline values
baseline_N_values = [3, 10, 100]
baseline_alpha_values = [0.0, 0.1, 1]

# Plot latency vs N for a fixed tle and different alphas
for alpha in baseline_alpha_values:
    subset = df[(df['alpha'] == alpha) & (df['tle'] == 1000)]
    if not subset.empty:
        axs[0].plot(subset['N'], subset['time'], 'o-', label=f"alpha = {alpha}")
axs[0].set_title('Latency vs N for tle=1000')
axs[0].set_xlabel('N')
axs[0].set_ylabel('Latency')
axs[0].legend()

# Plot latency vs tle for a fixed N and different alphas
for alpha in baseline_alpha_values:
    subset = df[(df['alpha'] == alpha) & (df['N'] == 100)]
    if not subset.empty:
        axs[1].plot(subset['tle'], subset['time'], 'o-', label=f"alpha = {alpha}")
axs[1].set_title('Latency vs tle for N=100')
axs[1].set_xlabel('tle')
axs[1].set_ylabel('Latency')
axs[1].legend()

# Save the plot to a file for each graph
plt.savefig('latency.png')
plt.show()