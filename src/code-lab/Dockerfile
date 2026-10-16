FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10003 --no-create-home runner
COPY engine.py runner.py server.py test_engine.py test_server.py samples.json ./
RUN python -m unittest -v test_engine test_server
USER 10003:10003
EXPOSE 8091
CMD ["python", "server.py"]
