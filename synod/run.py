import subprocess
import os
# Define the parameters to experiment with
N_values = [3, 10, 100]
f_values = [1, 4, 49]
alpha_values = [0, 0.1, 1]
tle_values = [500, 1000, 1500, 2000]

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

with open("system.log", "r") as file:
    for line in file:
        # Search for execution parameters
        match = re.search(r'System started with (N|tle|f|alpha)=([\d\.]+)', line)
        if match:
            print(f"{match.group(1)}: {match.group(2)}")

        # Search for the first decision time
        match = re.search(r'time: (\d+)', line)
        if match:
            print(f"First decision time: {match.group(1)}")


#plot the data
            