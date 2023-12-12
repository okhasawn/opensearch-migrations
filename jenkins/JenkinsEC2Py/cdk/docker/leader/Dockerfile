# Use the latest LTS version of Jenkins
FROM jenkins/jenkins:lts

# Switch to root to perform installations
USER root

# Update CA certificates
RUN apt-get update && \
    apt-get install -y ca-certificates python3 python3-pip python3-venv && \
    update-ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create a virtual environment and install Python packages
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip3 install jinja2 dnspython

# Copy custom plugin list and install plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

# Install plugins using jenkins-plugin-cli
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Copy configuration scripts
COPY modify_casc.py /modify_casc.py
COPY config-as-code.j2 /config-as-code.j2

# Create the config-as-code file and modify Jenkins startup script
RUN touch /config-as-code.yaml && \
    chown jenkins: /config-as-code.yaml && \
    sed -i '/\/bin\/bash*/a \\n\/modify_casc.py' /usr/local/bin/jenkins.sh

# Switch back to the Jenkins user
USER jenkins
