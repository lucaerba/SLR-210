import subprocess
import os
# Define the parameters to experiment with
N_values = [3, 10, 40, 100]
f_values = [1, 4, 19, 49]
alpha_values = [0, 0.1, 1]
tle_values = [1, 2, 3, 4,  5, 6, 7, 8, 10, 12, 15, 20, 25, 30, 35, 45, 65, 75, 85, 90, 95, 100, 150, 200, 300, 400, 500, 1000, 1500, 2000]
n_experiments = 50

def run_java():
    #remove and create system.log file
    if os.path.exists("system.log"):
        os.remove("system.log")
        print("system.log file removed")
    else:
        print("system.log file does not exist")

    #Repeat the experiment n_experiments times
    # Loop over all combinations of parameters
    for j in range(n_experiments):
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

#run_java()

import re
# Initialize parameters_list and times_list as lists of empty lists
parameters_list = []
times_list = [[] for _ in range(n_experiments)]
first = True
n_exp = 0
#do the mean over the n_experiments experiments
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
                first_shutting = True
            else:
                time_min = min(time_min, int(match.group(1)))
            print(f"Decision time: {match.group(1)}")

        match = re.search(r'System is shutting down...', line)
        if match:
            first = True
            if first_shutting is True:
                print(f"Decision time saved: {time_min}")
                times_list[n_exp].append(time_min)
                first_shutting = False

#average the times of the n_experiments experiments, to get the mean time of each experiment parameter, time_list has 5 arrays, one for each run
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

# Plot latency vs N for a fixed tle and different alphas
fig1, ax1 = plt.subplots(figsize=(30, 15))
for alpha in alpha_values:
    subset = df[(df['alpha'] == alpha) & (df['tle'] == 25)]
    if not subset.empty:
        ax1.plot(subset['N'], subset['time'], 'o-', label=f"alpha = {alpha}")
ax1.set_title('Latency vs N for tle=25')
ax1.set_xlabel('N')
ax1.set_ylabel('Latency')
ax1.legend()
plt.savefig('latency_vs_N.png')

# Plot latency vs tle for a fixed N and different alphas
fig2, ax2 = plt.subplots(figsize=(30, 15))
for alpha in alpha_values:
    subset = df[(df['alpha'] == alpha) & (df['N'] == 10)]
    if not subset.empty:
        ax2.plot(subset['tle'], subset['time'], 'o-', label=f"alpha = {alpha}")
ax2.set_title('Latency vs tle for N=10')
ax2.set_xlabel('tle')
ax2.set_ylabel('Latency')
ax2.legend()
plt.savefig('latency_vs_tle.png')

#Plot Latency vs tle for all N and fixed alpha focus on tle up to 100
fig3, ax3 = plt.subplots(figsize=(30, 15))
for N in N_values:
    subset = df[(df['alpha'] == 0.0) & (df['N'] == N) & (df['tle'] <= 150)]
    if not subset.empty:
        ax3.plot(subset['tle'], subset['time'], 'o-', label=f"N = {N}")
        #add a line to show the mean of the time for each N
        ax3.axhline(y=subset['time'].mean(),  linestyle='--', label=f"Mean N = {N}", color = 'black')
        
        
ax3.set_title('Latency vs tle for alpha=0.0')
ax3.set_xlabel('tle')
ax3.set_ylabel('Latency')
ax3.legend()
plt.savefig('latency_vs_tle_all_N_a00.png')

#Plot Latency vs tle for all N and fixed alpha focus on tle up to 100
fig3, ax3 = plt.subplots(figsize=(30, 15))
for N in N_values:
    subset = df[(df['alpha'] == 0.1) & (df['N'] == N) & (df['tle'] <= 150)]
    if not subset.empty:
        ax3.plot(subset['tle'], subset['time'], 'o-', label=f"N = {N}")
        #add a line to show the mean of the time for each N
        ax3.axhline(y=subset['time'].mean(),  linestyle='--', label=f"Mean N = {N}", color = 'black')
        
        
ax3.set_title('Latency vs tle for alpha=0.1')
ax3.set_xlabel('tle')
ax3.set_ylabel('Latency')
ax3.legend()
plt.savefig('latency_vs_tle_all_N_a01.png')

#Plot Latency vs tle for all N and fixed alpha focus on tle up to 100
fig3, ax3 = plt.subplots(figsize=(30, 15))
subset = df[(df['alpha'] == 0.1) & (df['N'] == 40) & (df['tle'] <= 100)]
if not subset.empty:
    ax3.plot(subset['tle'], subset['time'], 'o-', label=f"N = {40}")
    #add a line to show the mean of the time for each N
    ax3.axhline(y=subset['time'].mean(),  linestyle='--', label=f"Mean N = {40}", color = 'black')
    
ax3.set_title('Latency vs tle for alpha=0.1 and N=40')
ax3.set_xlabel('tle')
ax3.set_ylabel('Latency')
ax3.legend()
plt.savefig('latency_vs_tle_N40_a01.png')