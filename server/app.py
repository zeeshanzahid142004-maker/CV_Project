import io
import logging
import numpy as np

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image
import torch
from ultralytics import YOLO

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("logivision")

device = "cuda" if torch.cuda.is_available() else "cpu"
model = YOLO("yolo11s.pt").to(device)
logger.info(f"YOLO loaded on device: {device}")

VEHICLE_CLASSES = {"car", "motorcycle", "bus", "train", "truck", "boat"}

app = FastAPI(title="LogiVision Detection API")


@app.get("/")
def health():
    return {"status": "ok", "model": "yolov11s", "tracking": "bytetrack"}


@app.post("/detect")
async def detect(image: UploadFile = File(...)):
    try:
        contents = await image.read()
        img = Image.open(io.BytesIO(contents)).convert("RGB")
        img_np = np.array(img)
        img_width, img_height = img.size

        vehicle_class_ids = [2, 3, 5, 6, 7, 8]

        results = model.track(
            source=img_np,
            conf=0.25,
            iou=0.4,
            persist=True,
            tracker="bytetrack.yaml",
            classes=vehicle_class_ids,
            verbose=False,
        )

        detections = []
        for result in results:
            if result.boxes is None or len(result.boxes) == 0:
                continue

            has_ids = result.boxes.id is not None

            for i, box in enumerate(result.boxes):
                cls_id = int(box.cls[0])
                cls_name = result.names[cls_id]

                track_id = int(result.boxes.id[i]) if has_ids else -1

                xyxyn = box.xyxyn[0].tolist()
                detections.append(
                    {
                        "box": xyxyn,
                        "track_id": track_id,
                        "class": cls_name,
                        "confidence": round(float(box.conf[0]), 3),
                    }
                )

        logger.info(
            f"Tracked {len(detections)} vehicles in {img_width}x{img_height} image"
        )

        return JSONResponse(
            content={
                "detections": detections,
                "image_width": img_width,
                "image_height": img_height,
            }
        )

    except Exception as e:
        logger.error(f"Detection failed: {e}")
        return JSONResponse(content={"error": str(e)}, status_code=500)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=80)
