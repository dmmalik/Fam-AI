import pytest

from farm_ai.vision.severity import severity_for
from farm_ai.vision.types import INPUT_SIZE, PreparedImage

from conftest import jpeg_bytes, png_bytes


@pytest.mark.parametrize(
    ("ratio", "expected"),
    [
        (0.0, "low"),
        (0.09, "low"),
        (0.10, "medium"),
        (0.30, "medium"),
        (0.31, "high"),
    ],
)
def test_disease_uses_lesion_area(ratio, expected):
    assert severity_for("disease", ratio, 0.99) == expected


def test_pest_uses_confidence_not_lesion_area():
    assert severity_for("pest", 0.99, 0.59) == "low"
    assert severity_for("pest", 0.0, 0.60) == "medium"
    assert severity_for("pest", 0.0, 0.849) == "medium"
    assert severity_for("pest", 0.0, 0.85) == "high"


def test_network_tensor_is_imagenet_nchw():
    prepared = PreparedImage.from_bytes(png_bytes(color=(0, 0, 0)))
    assert prepared.nchw.shape == (1, 3, INPUT_SIZE, INPUT_SIZE)
    assert prepared.nchw.dtype.name == "float32"
    assert prepared.rgb_uint8.shape == (INPUT_SIZE, INPUT_SIZE, 3)
    assert prepared.rgb_uint8.dtype.name == "uint8"
    expected = (0.0 - 0.485) / 0.229
    assert prepared.nchw[0, 0, 0, 0] == pytest.approx(expected, rel=1e-5)


def test_lesion_ratio_on_leaf_colors():
    green = PreparedImage.from_bytes(png_bytes(color=(30, 150, 40)))
    brown = PreparedImage.from_bytes(png_bytes(color=(150, 70, 30)))
    white = PreparedImage.from_bytes(png_bytes(color=(245, 245, 245)))
    assert green.lesion_ratio < 0.10
    assert brown.lesion_ratio > 0.30
    assert white.lesion_ratio == 0.0
    assert severity_for("disease", green.lesion_ratio, 0.4) == "low"
    assert severity_for("disease", brown.lesion_ratio, 0.4) == "high"


def test_jpeg_and_png_headers():
    PreparedImage.from_bytes(jpeg_bytes())
    with pytest.raises(ValueError):
        PreparedImage.from_bytes(b"GIF89a not an image")
    with pytest.raises(ValueError):
        PreparedImage.from_bytes(b"not an image at all")
