import unittest

from chat_client.advertising import MulticastAdvertisementListener


class AdvertisingFilenameTests(unittest.TestCase):
    def test_accepts_supported_image_formats(self):
        for filename in (
            "banner.png",
            "campaign.gif",
            "photo.jpg",
            "photo.jpeg",
            "banner.webp",
        ):
            with self.subTest(filename=filename):
                self.assertTrue(MulticastAdvertisementListener._valid_filename(filename))

    def test_accepts_case_insensitive_extensions(self):
        self.assertTrue(MulticastAdvertisementListener._valid_filename("Campaign.GIF"))

    def test_rejects_path_traversal(self):
        for filename in ("../banner.png", "..\\banner.png", "folder/banner.png"):
            with self.subTest(filename=filename):
                self.assertFalse(MulticastAdvertisementListener._valid_filename(filename))

    def test_rejects_unknown_or_malformed_names(self):
        for filename in ("banner.svg", "banner.png?x=1", "", "a" * 200 + ".png"):
            with self.subTest(filename=filename):
                self.assertFalse(MulticastAdvertisementListener._valid_filename(filename))


if __name__ == "__main__":
    unittest.main()
