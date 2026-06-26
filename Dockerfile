FROM eclipse-temurin:25-jdk-jammy
LABEL authors="mvdcamme"

# Download dependencies
RUN apt-get update && \
    apt-get install -yqq curl scala python3 bzip2 apt-transport-https gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import && \
    chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg && \
    apt-get update && \
    apt-get install -y sbt

# Install uv as a separate dependency
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

WORKDIR /app
ENV INFRAMAPPER_HOME="/app"
# Find uv in PATH
ENV PATH="/root/.local/bin:$PATH"
COPY . .

RUN mkdir "cache"

# Compile InfraMapper
RUN sbt assembly

# Download and install scansible plugin
RUN mkdir -p plugins/scansible && \
    cd plugins && \
    curl -L "https://github.com/softwarelanguageslab/scansible/archive/refs/tags/0.1.2.tar.gz" -o scansible.tar.gz && \
    tar -xf scansible.tar.gz -C scansible --strip-components 1 && \
    rm -rf scansible.tar.gz && \
    cd scansible && \
    uv sync && \
    cd DependencyPatternMatcher && \
    sbt assembly

ENTRYPOINT ["sh", "InfraMapper_rest_api"]