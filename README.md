
# Requirements
## A machine running Linux/macOS with Docker installed OR use a VM with Linux

## Directory Structure
Make sure scripts are executable before running by using `chmod +x <script>`

```
/ (project root)
├── build.sh                # Main script build all Docker images for the Hadoop cluster
├── run.sh                  # Script that starts the MapReduce job from 'submit/'
├── docker-compose.yml      # Defines the Hadoop cluster (NameNode, DataNodes, ResourceManager, NodeManagers) via Docker Compose
├── hadoop.env              # Configuration file for Hadoop environment variables
│
├── base/                   # Hadoop cluster def; can be ignored
├── datanode/               # Hadoop cluster def; can be ignored
├── historyserver/          # Hadoop cluster def; can be ignored
├── namenode/               # Hadoop cluster def; can be ignored
├── nodemanager/            # Hadoop cluster def; can be ignored
├── resourcemanager/        # Hadoop cluster def; can be ignored
│
├── datanode_separate/      # Used for adding a datanode from a different computer
│
├── inverse_index/          # Contains Java source, build scripts, and init script for the compile-and-test container
│   ├── run-all.sh          # Automatically calls the two scripts below to compile the program; index.jar will be placed automatically in 'submit/'
│   ├── build.sh            # Can be ignored; compiles WordCounter.java inside the "hadoop-compiler" container, producing index.jar
│   ├── init.sh             # Can be ignored; launches a live Hadoop environment inside the "hadoop-compiler" container for testing/debugging
│   ├── InverseIndex.java    # Mapper, Combiner, Reducer, and driver code
│   └── stopwords.txt       # List of words to ignore (e.g., "the", "and", ...)
│
└── submit/                 # Container definition for uploading the books to HDFS and starting the MapReduce job
    ├── Dockerfile          # Builds the "hadoop-wordcounter" image
    ├── index.jar           # JAR containing compiled WordCounter classes and stopwords.txt
    ├── run.sh              # Script to copy the concatenated file into HDFS, show block locations, run WordCounter, and retrieve results
    └── download-books.sh   # Script that downloads 10 books (we should change them)
```

## Running the app

### 1. Build all images
```bash
cd <project_root>
sudo ./build.sh
```
### 2. Start Hadoop cluster
Modify the datanodes' IP address in docker-compose.yml!
```bash
sudo docker compose up
```
### 3. Run MapReduce program
```bash
cd <project_root>
sudo ./run.sh
```

Results will be saved in a result.txt file

## Compiling
In `wordcounter/`, you just have to call the script `run-all.sh`. `WordCounter.java` contains the actual Java code for the MapReduce program.

## Adding a datanode from another machine
Within the `datanode_separate` folder, run `docker compose up`. 

Make sure you have configured `hadoop.env` beforehand (I've left some comments where you need to change the IP address of the master)