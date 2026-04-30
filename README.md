```bash
# 1. Build all images
cd <project_root>
chmod +x build.sh
bash build.sh

# 2. Start Hadoop cluster
docker-compose up

# 3. Compile WordCounter
cd wordcounter
bash run-all.sh   # produces submit/index.jar




# Next
folder-ul fml/ are niste carti descarcate si un script build.sh care ar trebui sa creeze un container care incarca fisierele in HDFS si ruleaza acel WordCounter - inca nu imi merge