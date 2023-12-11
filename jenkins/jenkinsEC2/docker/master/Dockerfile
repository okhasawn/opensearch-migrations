FROM jenkins/jenkins:lts

# Copy plugin list
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

# Install plugins using jenkins-plugin-cli
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Switch to root user for installations
USER root

# Install Python 3 and pip
RUN apt-get update && \
    apt-get install -y python3-pip python3-venv && \
    rm -rf /var/lib/apt/lists/*

# Create a Python virtual environment and install packages
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip3 install jinja2 dnspython

# Install Node.js
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs && \
    node -v && npm -v

# Install Firebase Tools
RUN npm install -g firebase-tools


# Apply configuration-as-code
COPY config-as-code.yaml /config-as-code.yaml

# Switch back to the jenkins user
USER jenkins
