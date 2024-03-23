import subprocess

# Define the parameters to experiment with
N_values = [3, 10, 100]
f_values = [1, 4, 49]
alpha_values = [0, 0.1, 1]
tle_values = [500, 1000, 1500, 2000]

# Loop over all combinations of parameters
for N in N_values:
    for f in f_values:
        for alpha in alpha_values:
            for tle in tle_values:
                # Construct the arguments as a single string
                arguments = f"-DN={N} -Df={f} -Dalpha={alpha} -Dtle={tle}"
                #mvn exec:java -Dexec.mainClass="com.myPackage.myClass" -Dexec.args="command line arguments"

                # Run the Java program with the current parameters using Maven
                command = ["mvn", "exec:java", "-Dexec.mainClass=com.example.synod.Main", f"-Dexec.args={arguments}"]
                print("Running command:", " ".join(command))
                subprocess.run(command)
