import subprocess
import os
# Define the parameters to experiment with
N_values = [3, 10, 100]
f_values = [1, 4, 49]
alpha_values = [0, 0.1, 1]
tle_values = [500, 1000, 1500, 2000]

def run_java():
    #remove and create system.log file
    if os.path.exists("system.log"):
        os.remove("system.log")
        print("system.log file removed")
    else:
        print("system.log file does not exist")

    # Loop over all combinations of parameters
    for j in range(1):
        for i in range(len(N_values)):
            for alpha in alpha_values:
                for tle in tle_values:
                    # Construct the arguments as a single string
                    
                    # Run the Java program with the current parameters using Maven
                    command = ["mvn", "exec:exec", f"-DN={N_values[i]}", f"-Df={f_values[i]}",f"-Dalpha={alpha}",f"-Dtle={tle}"]
                    print("Running command:", " ".join(command))
                    #run them sequentially not in parallel
                    subprocess.run(command)
                
import re
parameters_list = []
times_list = []
stop = False

with open("system.log", "r") as file:
    for line in file:
        # Search for execution parameters and save everything in a dictionary
        # 19:51:45.098 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with N=100
        #19:51:45.099 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with tle=2000
        #19:51:45.099 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with f=49
        #19:51:45.100 [system-akka.actor.default-dispatcher-6] INFO akka.actor.ActorSystemImpl - System started with alpha=0.0
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

        # Search for the first decision time
        match = re.search(r'time: (\d+)', line)
        if match and not stop:
            print(f"First decision time: {match.group(1)}")
            times_list.append(int(match.group(1)))
            stop = True

        match = re.search(r'System is shutting down...', line)
        if match:
            stop = False

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

# Create a figure with 3 subplots, one for each parameter
fig, axs = plt.subplots(3)

# Plot latency vs N
for N in df['N'].unique():
    mean_time = df[df['N'] == N]['time'].mean()
    axs[0].plot(N, mean_time, 'o')
axs[0].set_title('Latency vs N')

# Plot latency vs tle
for tle in df['tle'].unique():
    mean_time = df[df['tle'] == tle]['time'].mean()
    axs[1].plot(tle, mean_time, 'o')
axs[1].set_title('Latency vs tle')

# Plot latency vs alpha
for alpha in df['alpha'].unique():
    mean_time = df[df['alpha'] == alpha]['time'].mean()
    axs[2].plot(alpha, mean_time, 'o')
axs[2].set_title('Latency vs alpha')

# Show the plots
plt.show()