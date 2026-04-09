-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 03:00 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `regulation_audit_history`
--

CREATE TABLE `regulation_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `regulation_audit_history`
--

INSERT INTO `regulation_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(7, 1, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'dfghdgfg', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 2, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'REG-7', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 3, 'Regulation', 'Details', 'Added', 'Description', NULL, 'dfgdfgdfg', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 4, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 5, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 6, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 7, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 8, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 9, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 10, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 11, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 12, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-10-15', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 13, 'Regulation', 'Details', 'Added', 'Comments Date', NULL, '2025-10-10', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 14, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-22', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 15, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 12:14:54', '2025-10-29 12:14:54'),
(7, 16, 'Regulation', 'Details', 'Updated', 'Primary Name', 'dfghdgfg', 'dfghdgfg2', 'Alice Smith', '2025-10-29 12:15:43', '2025-10-29 12:15:43'),
(7, 17, 'Regulation', 'Details', 'Updated', 'Impact Rating', 'impact r', NULL, 'Alice Smith', '2025-10-29 12:15:43', '2025-10-29 12:15:43'),
(8, 18, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'ahmed', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 19, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'REG-8', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 20, 'Regulation', 'Details', 'Added', 'Description', NULL, 'mohamed', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 21, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 22, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 23, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 24, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 25, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 26, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 27, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 28, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 29, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-10-29', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 30, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-22', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 31, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 12:18:27', '2025-10-29 12:18:27'),
(8, 32, 'Regulation', 'Details', 'Updated', 'Primary Name', 'ahmed', 'ahmed2', 'Alice Smith', '2025-10-29 12:19:36', '2025-10-29 12:19:36'),
(8, 33, 'Regulation', 'Details', 'Updated', 'Impact Rating', 'impact r', NULL, 'Alice Smith', '2025-10-29 12:19:36', '2025-10-29 12:19:36'),
(9, 34, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'سيبسيبسي', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 35, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'بسيبس', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 36, 'Regulation', 'Details', 'Added', 'Description', NULL, 'سيبسي', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 37, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 38, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 39, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 40, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 41, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 42, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 43, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 44, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 45, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-10-22', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 46, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-22', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 47, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 12:27:28', '2025-10-29 12:27:28'),
(9, 48, 'Regulation', 'Details', 'Updated', 'Primary Name', 'سيبسيبسي', 'fdgdfgdf', 'Alice Smith', '2025-10-29 12:27:39', '2025-10-29 12:27:39'),
(10, 49, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'asddsa', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 50, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'REG-10', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 51, 'Regulation', 'Details', 'Added', 'Description', NULL, 'asddas', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 52, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 53, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 54, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 55, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 56, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 57, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 58, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 59, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 60, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-10-29', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 61, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-30', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 62, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 12:56:05', '2025-10-29 12:56:05'),
(10, 63, 'Regulation', 'Details', 'Updated', 'Primary Name', 'asddsa', 'asddsa2', 'Alice Smith', '2025-10-29 12:57:57', '2025-10-29 12:57:57'),
(10, 64, 'Regulation', 'Details', 'Updated', 'Parent Regulation', NULL, 'fdgdfgdf', 'Alice Smith', '2025-10-29 12:58:50', '2025-10-29 12:58:50'),
(11, 65, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'لاتناناتنم', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 66, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'تنم', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 67, 'Regulation', 'Details', 'Added', 'Description', NULL, 'تناماتنم', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 68, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 69, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 70, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 71, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 72, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 73, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 74, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 75, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 76, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-10-28', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 77, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-29', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(11, 78, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 12:59:26', '2025-10-29 12:59:26'),
(12, 79, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'fsdsdfsdfsfgfgdg', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 80, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'REG-12', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 81, 'Regulation', 'Details', 'Added', 'Description', NULL, 'hfghfgh', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 82, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 83, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 84, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 85, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 86, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 87, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 88, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 89, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 90, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-10-29', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 91, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-30', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 92, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 93, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Regulation Owner', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 94, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(12, 95, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 13:02:30', '2025-10-29 13:02:30'),
(13, 96, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'reg_test4', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 97, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'REG-13', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 98, 'Regulation', 'Details', 'Added', 'Short Name', NULL, 'shortttt', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 99, 'Regulation', 'Details', 'Added', 'Description', NULL, '9-11', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 100, 'Regulation', 'Details', 'Added', 'Additional Info', NULL, 'infooo', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 101, 'Regulation', 'Details', 'Added', 'Parent Regulation', NULL, 'regulation3', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 102, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 103, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 104, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 105, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 106, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 107, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 108, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 109, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 110, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-11-09', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 111, 'Regulation', 'Details', 'Added', 'Comments Date', NULL, '2025-09-11', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 112, 'Regulation', 'Details', 'Added', 'Finalisation Date', NULL, '2025-06-29', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 113, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-22', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 114, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 14:40:35', '2025-11-09 14:40:35'),
(13, 115, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Regulation Owner', 'Alice Smith', '2025-11-09 14:40:36', '2025-11-09 14:40:36'),
(13, 116, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 14:40:36', '2025-11-09 14:40:36'),
(13, 117, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 14:40:36', '2025-11-09 14:40:36'),
(13, 139, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-09 15:07:05', '2025-11-09 15:07:05'),
(13, 140, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Regulation Owner', 'Alice Smith', '2025-11-09 15:07:05', '2025-11-09 15:07:05'),
(13, 141, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 15:07:05', '2025-11-09 15:07:05'),
(13, 142, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'Alice Smith', '2025-11-09 15:07:05', '2025-11-09 15:07:05'),
(13, 143, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Regulation Owner', NULL, 'Alice Smith', '2025-11-09 15:07:05', '2025-11-09 15:07:05'),
(13, 144, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'Alice Smith', '2025-11-09 15:07:05', '2025-11-09 15:07:05'),
(14, 118, 'Regulation', 'Details', 'Added', 'Primary Name', NULL, 'REGULATION_test4', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 119, 'Regulation', 'Details', 'Added', 'Ref Number', NULL, 'REG-14', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 120, 'Regulation', 'Details', 'Added', 'Short Name', NULL, 'shortttt', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 121, 'Regulation', 'Details', 'Added', 'Description', NULL, '9-11', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 122, 'Regulation', 'Details', 'Added', 'Parent Regulation', NULL, 'regulation2', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 123, 'Regulation', 'Details', 'Status Change', 'Status', NULL, 'status', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 124, 'Regulation', 'Details', 'Added', 'Stage', NULL, 'stag', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 125, 'Regulation', 'Details', 'Added', 'Maturity', NULL, 'mat', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 126, 'Regulation', 'Details', 'Added', 'Probability', NULL, 'prob', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 127, 'Regulation', 'Details', 'Added', 'Compliance Level', NULL, 'cl', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 128, 'Regulation', 'Details', 'Added', 'Impact Rating', NULL, 'impact r', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 129, 'Regulation', 'Details', 'Added', 'Legal Advice Type', NULL, 'leg', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 130, 'Regulation', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 131, 'Regulation', 'Details', 'Added', 'Publication Date', NULL, '2025-11-09', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 132, 'Regulation', 'Details', 'Added', 'Comments Date', NULL, '2025-09-11', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 133, 'Regulation', 'Details', 'Added', 'Finalisation Date', NULL, '2025-06-29', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 134, 'Regulation', 'Details', 'Added', 'Compliance Date', NULL, '2025-10-22', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 135, 'Regulation', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 136, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Regulation Owner', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 137, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43'),
(14, 138, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 14:48:43', '2025-11-09 14:48:43');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `regulation_audit_history`
--
ALTER TABLE `regulation_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `regulation_audit_history`
--
ALTER TABLE `regulation_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=145;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `regulation_audit_history`
--
ALTER TABLE `regulation_audit_history`
  ADD CONSTRAINT `regulation_audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
