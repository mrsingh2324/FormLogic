"""GCS storage service — replaces services/storageService.ts"""
import io
import os
import uuid
from app.utils.logger import logger

BUCKET_NAME = os.getenv("GCS_BUCKET_NAME", "formlogic-uploads")
MAX_DIMENSION = 1200
JPEG_QUALITY = 82


async def upload_image(data: bytes, mime_type: str, folder: str) -> dict:
    from google.cloud import storage
    from PIL import Image

    # Process image
    img = Image.open(io.BytesIO(data))
    img.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.LANCZOS)
    output = io.BytesIO()
    img.convert("RGB").save(output, format="JPEG", quality=JPEG_QUALITY, optimize=True)
    processed = output.getvalue()

    filename = f"{folder}/{uuid.uuid4()}.jpg"
    client = storage.Client()
    bucket = client.bucket(BUCKET_NAME)
    blob = bucket.blob(filename)
    blob.upload_from_string(processed, content_type="image/jpeg")
    blob.make_public()

    public_url = f"https://storage.googleapis.com/{BUCKET_NAME}/{filename}"
    logger.info(f"Uploaded image: {public_url} ({len(processed)} bytes)")
    return {"public_url": public_url, "gcs_path": filename, "size_bytes": len(processed)}


async def delete_image(gcs_path: str):
    try:
        from google.cloud import storage
        client = storage.Client()
        client.bucket(BUCKET_NAME).blob(gcs_path).delete()
        logger.info(f"Deleted GCS file: {gcs_path}")
    except Exception as e:
        logger.warning(f"Failed to delete GCS file {gcs_path}: {e}")
